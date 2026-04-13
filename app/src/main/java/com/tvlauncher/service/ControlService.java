package com.tvlauncher.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.tvlauncher.MainActivity;
import com.tvlauncher.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ControlService extends Service {
    private static final String TAG = "ControlService";
    private static final int PORT = 7816;
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "TVLauncherChannel";

    private ServerSocket serverSocket;
    private boolean isRunning = true;
    private SharedPreferences prefs;

    private String currentAutoStartApp = "com.example.liveapp";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("TVLauncher", MODE_PRIVATE);
        currentAutoStartApp = prefs.getString("auto_start_app", "com.tvlauncher");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // Start socket server in background thread
        new Thread(new SocketServer()).start();
        
        Log.d(TAG, "ControlService started on port " + PORT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TV Launcher Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the remote control service running");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setContentTitle("TV Launcher")
            .setContentText("Remote control active on port " + PORT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(Notification.PRIORITY_LOW)
            .build();
    }

    /**
     * Socket Server Thread - Handles incoming TCP connections
     */
    private class SocketServer implements Runnable {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Server listening on port " + PORT);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(new ClientHandler(clientSocket)).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to start server socket", e);
            }
        }
    }

    /**
     * Client Handler - Processes individual client connections
     */
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private static final int TIMEOUT_MS = 30000; // 30 second timeout
        private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10 second heartbeat

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                clientSocket.setSoTimeout(TIMEOUT_MS);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
                );
                BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream())
                );

                Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());

                while (isRunning) {
                    String command = reader.readLine();
                    if (command == null) break;

                    Log.d(TAG, "Received command: " + command);
                    String response = processCommand(command.trim());
                    writer.write(response + "\n");
                    writer.flush();
                }

                reader.close();
                writer.close();
                clientSocket.close();
                Log.d(TAG, "Client disconnected");
            } catch (IOException e) {
                Log.e(TAG, "Client handler error", e);
            }
        }

        private String processCommand(String command) {
            String[] parts = command.split(":", 2);
            String cmd = parts[0].toUpperCase();
            String arg = parts.length > 1 ? parts[1] : "";

            switch (cmd) {
                case "CMD_PING":
                    return "OK:PONG";

                case "CMD_GET_STATUS":
                    return getDeviceStatus();

                case "CMD_CHANGE_APP":
                    return changeAutoStartApp(arg);

                case "CMD_GET_APPS":
                    return getInstalledApps();

                case "CMD_LAUNCH":
                    return launchApp(arg);

                case "CMD_REBOOT":
                    return rebootDevice();

                case "CMD_GET_AUTO_START":
                    return "OK:" + currentAutoStartApp;

                default:
                    return "ERR:Unknown command";
            }
        }

        private String getDeviceStatus() {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            String networkStatus = (netInfo != null && netInfo.isConnected()) ? "Connected" : "Disconnected";

            String status = String.format(
                "OK:{\"status\":\"running\"," +
                "\"app\":\"%s\"," +
                "\"network\":\"%s\"," +
                "\"version\":\"1.0\"," +
                "\"platform\":\"Android 4.4\"}",
                currentAutoStartApp, networkStatus
            );
            return status;
        }

        private String changeAutoStartApp(String packageName) {
            if (packageName == null || packageName.isEmpty()) {
                return "ERR:Package name required";
            }
            currentAutoStartApp = packageName;
            prefs.edit().putString("auto_start_app", packageName).apply();
            Log.d(TAG, "Auto-start app changed to: " + packageName);
            return "OK:Auto-start app changed to " + packageName;
        }

        private String getInstalledApps() {
            List<String> apps = new ArrayList<>();
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo app : packages) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    // User-installed apps
                    apps.add(app.packageName);
                }
            }

            return "OK:" + String.join(",", apps);
        }

        private String launchApp(String packageName) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return "OK:Launched " + packageName;
                }
                return "ERR:App not found";
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch app", e);
                return "ERR:" + e.getMessage();
            }
        }

        private String rebootDevice() {
            try {
                Intent intent = new Intent(Intent.ACTION_REBOOT);
                intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return "OK:Rebooting...";
            } catch (Exception e) {
                return "ERR:Requires system permission";
            }
        }
    }
}

package com.tvlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "TVLauncher";
    private TextView tvStatus;
    private Button btnRefresh;
    private String autoStartApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnRefresh = findViewById(R.id.btn_refresh);

        loadPreferences();
        updateStatus();

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus();
            }
        });

        // Start control service
        Intent serviceIntent = new Intent(this, com.tvlauncher.service.ControlService.class);
        startService(serviceIntent);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences("TVLauncher", MODE_PRIVATE);
        autoStartApp = prefs.getString("auto_start_app", "com.tvlauncher");
    }

    private void updateStatus() {
        String info = "TV Launcher v1.0\n" +
                      "Auto-start App: " + autoStartApp + "\n" +
                      "Control Port: 7816\n" +
                      "Status: Running";
        tvStatus.setText(info);
    }

    public void launchApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + packageName, e);
        }
    }
}

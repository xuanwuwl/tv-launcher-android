package com.tvlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) ||
            intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON")) {
            
            Log.d(TAG, "Boot completed, starting auto-launch app");

            // Start main activity which handles auto-launch
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mainIntent);

            // Also start control service
            Intent serviceIntent = new Intent(context, service.ControlService.class);
            context.startService(serviceIntent);
        }
    }
}

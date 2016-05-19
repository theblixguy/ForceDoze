package com.suyashsrijan.forcedoze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class AutoRestartOnUpdate extends BroadcastReceiver {
    public static String TAG = "ForceDoze";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED) && intent.getDataString().contains(context.getPackageName())) {
            Log.i(TAG, "Application updated, restarting service if enabled");
            boolean isServiceEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false);
            if (isServiceEnabled) {
                if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
                    context.stopService(new Intent(context, ForceDozeService.class));
                    context.startService(new Intent(context, ForceDozeService.class));
                } else if (!Utils.isMyServiceRunning(ForceDozeService.class, context)) {
                    context.startService(new Intent(context, ForceDozeService.class));
                }
            } else {
                Log.i(TAG, "Service not enabled, skip restarting");
            }
        }
    }
}

package com.suyashsrijan.forcedoze;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
    public static String TAG = "ForceDoze";
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isServiceEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false);
        Log.i(TAG, "Received BOOT_COMPLETED intent, isServiceEnabled=" + Boolean.toString(isServiceEnabled));
        if (isServiceEnabled) {
            Intent startServiceIntent = new Intent(context, ForceDozeService.class);
            context.startService(startServiceIntent);
        }
    }
}

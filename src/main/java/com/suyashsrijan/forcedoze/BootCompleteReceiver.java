package com.suyashsrijan.forcedoze;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class BootCompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isServiceEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("serviceEnabled", false);
        if (isServiceEnabled) {
            Intent startServiceIntent = new Intent(context, ForceDozeService.class);
            context.startService(startServiceIntent);
        }
    }
}

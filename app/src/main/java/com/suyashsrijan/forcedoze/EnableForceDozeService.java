package com.suyashsrijan.forcedoze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class EnableForceDozeService extends BroadcastReceiver {
    public static String TAG = "ForceDoze";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "com.suyashsrijan.forcedoze.ENABLE_FORCEDOZE broadcast intent received");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("serviceEnabled", true).apply();
        if (!Utils.isMyServiceRunning(ForceDozeService.class, context)) {
            context.startService(new Intent(context, ForceDozeService.class));
        }
    }
}

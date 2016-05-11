package com.suyashsrijan.forcedoze;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startServiceIntent = new Intent(context, ForceDozeService.class);
        context.startService(startServiceIntent);
    }
}

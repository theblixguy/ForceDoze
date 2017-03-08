package com.suyashsrijan.forcedoze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class RemoveWhiteListReceiver extends BroadcastReceiver {
    public static String TAG = "ForceDoze";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "com.suyashsrijan.forcedoze.REMOVE_WHITELIST broadcast intent received");
        final String packageName = intent.getStringExtra("packageName");
        Log.i(TAG, "Package name received: " + packageName);
        if (packageName != null) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SH.run("dumpsys deviceidle whitelist -" + packageName);
                    if (output != null) {
                        for (String s : output) {
                            Log.i(TAG, s);
                        }
                    } else {
                        Log.i(TAG, "Error occurred while executing command (" + "dumpsys deviceidle whitelist -packagename" + ")");
                    }
                }
            });
        } else {
            Log.i(TAG, "Package name null or empty");
        }
    }
}

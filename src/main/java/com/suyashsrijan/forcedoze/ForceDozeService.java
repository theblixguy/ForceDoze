package com.suyashsrijan.forcedoze;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import eu.chainfire.libsuperuser.Shell;

public class ForceDozeService extends Service {

    boolean isDozing = false;
    Handler localHandler;
    Runnable enterDozeRunnable;
    DozeReceiver localDozeReceiver;
    public static String TAG = "ForceDozeService";
    public ForceDozeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localDozeReceiver = new DozeReceiver();
        localHandler = new Handler();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        this.registerReceiver(localDozeReceiver, filter);
        grantDumpPermission();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service");
        this.unregisterReceiver(localDozeReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public void grantDumpPermission() {
        Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
        Shell.SU.run("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
    }

    public void enterDoze() {
        isDozing = true;
        Log.i(TAG, "Entering Doze and disabling motion sensors");
        Shell.SU.run("dumpsys deviceidle force-idle");
        Shell.SU.run("dumpsys sensorservice restrict null");
    }

    public void exitDoze() {
        isDozing = false;
        Log.i(TAG, "Exiting Doze and re-enabling motion sensors");
        Shell.SU.run("dumpsys deviceidle step");
        Shell.SU.run("dumpsys sensorservice enable");
    }

    class DozeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int time = Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000);

            enterDozeRunnable = new Runnable() {
                @Override
                public void run() {
                    enterDoze();
                }
            };

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.i(TAG, "Screen ON received");
                if (isDozing) {
                    exitDoze();
                } else {
                    Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(time) + "ms has not passed");
                    localHandler.removeCallbacks(enterDozeRunnable);
                }
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.i(TAG, "Screen OFF received");
                Log.i(TAG, "Waiting for " + Integer.toString(time) + "ms and then entering Doze");
                localHandler.postDelayed(enterDozeRunnable, time);
            }
        }
    }



}

package com.suyashsrijan.forcedoze;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import eu.chainfire.libsuperuser.Shell;

public class ForceDozeService extends Service {

    boolean isDozing = false;
    boolean isSuAvailable = false;
    boolean disableWhenCharging = true;
    boolean enableSensors = false;
    Handler localHandler;
    Runnable enterDozeRunnable;
    Runnable disableSensorsRunnable;
    Runnable enableSensorsRunnable;
    DozeReceiver localDozeReceiver;
    Set<String> dozeUsageData;
    String sensorWhitelistPackage = "";
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
        sensorWhitelistPackage = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        enableSensors = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        disableWhenCharging = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
        isSuAvailable = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("isSuAvailable", false);
        dozeUsageData = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageData", new LinkedHashSet<String>());
        if (!Utils.isDumpPermissionGranted(getApplicationContext())) {
            if (isSuAvailable) {
                grantDumpPermission();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service and enabling sensors");
        this.unregisterReceiver(localDozeReceiver);
        if (!enableSensors) {
            executeCommand("dumpsys sensorservice enable");
        }
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
        Log.i(TAG, "Entering Doze");
        executeCommand("dumpsys deviceidle force-idle");
        dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel(getApplicationContext()))).concat(",").concat("ENTER"));
        saveDozeDataStats();
        disableSensorsRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Disabling motion sensors");
                if (sensorWhitelistPackage.equals("")) {
                    executeCommand("dumpsys sensorservice restrict null");
                } else {
                    Log.i(TAG, "Package " + sensorWhitelistPackage + " is whitelisted from sensorservice");
                    Log.i(TAG, "Note: Packages that get whitelisted are supposed to request sensor access again, if the app doesn't work, email the dev of that app!");
                    executeCommand("dumpsys sensorservice restrict " + sensorWhitelistPackage);
                }
            }
        };
        if (!enableSensors) {
            localHandler.postDelayed(disableSensorsRunnable, 2000);
        }
    }

    public void exitDoze() {
        isDozing = false;
        Log.i(TAG, "Exiting Doze");
        executeCommand("dumpsys deviceidle step");
        dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel(getApplicationContext()))).concat(",").concat("EXIT"));
        saveDozeDataStats();
        enableSensorsRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Re-enabling motion sensors");
                executeCommand("dumpsys sensorservice enable");
            }
        };
        if (!enableSensors) {
            localHandler.postDelayed(enableSensorsRunnable, 2000);
        }

    }

    public void executeCommand(String command) {
        if (isSuAvailable) {
            Shell.SU.run(command);
        } else {
            try {
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void saveDozeDataStats() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("dozeUsageData");
        editor.apply();
        editor.putStringSet("dozeUsageData", dozeUsageData);
        editor.apply();
    }

    class DozeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // This is to prevent Doze from kicking before the OS locks the screen
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
                    Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(time) + "ms has not passed or disableWhenCharging=true");
                    localHandler.removeCallbacks(enterDozeRunnable);
                }
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.i(TAG, "Screen OFF received");
                if (Utils.isConnectedToCharger(getApplicationContext()) && disableWhenCharging) {
                    Log.i(TAG, "Connected to charger and disableWhenCharging=true, skip entering Doze");
                } else {
                    Log.i(TAG, "Waiting for " + Integer.toString(time) + "ms and then entering Doze");
                    localHandler.postDelayed(enterDozeRunnable, time);
                }
            }
        }
    }

}

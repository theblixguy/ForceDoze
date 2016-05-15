package com.suyashsrijan.forcedoze;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import eu.chainfire.libsuperuser.Shell;

public class ForceDozeService extends Service {

    boolean isSuAvailable = false;
    boolean disableWhenCharging = true;
    boolean enableSensors = false;
    boolean useAutoRotateAndBrightnessFix = false;
    int dozeEnterDelay = 0;
    Handler localHandler;
    Runnable enterDozeRunnable;
    Runnable disableSensorsRunnable;
    Runnable enableSensorsRunnable;
    DozeReceiver localDozeReceiver;
    ReloadSettingsReceiver reloadSettingsReceiver;
    Set<String> dozeUsageData;
    String sensorWhitelistPackage = "";
    public static String TAG = "ForceDozeService";

    public ForceDozeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localDozeReceiver = new DozeReceiver();
        reloadSettingsReceiver = new ReloadSettingsReceiver();
        localHandler = new Handler();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        LocalBroadcastManager.getInstance(this).registerReceiver(reloadSettingsReceiver, new IntentFilter("reload-settings"));
        this.registerReceiver(localDozeReceiver, filter);
        dozeEnterDelay = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        useAutoRotateAndBrightnessFix = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reloadSettingsReceiver);
        if (!enableSensors) {
            executeCommand("dumpsys sensorservice enable");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Service has now started");
        return START_STICKY;
    }

    public void reloadSettings() {
        dozeEnterDelay = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        useAutoRotateAndBrightnessFix = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
        sensorWhitelistPackage = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        enableSensors = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        disableWhenCharging = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
    }

    public void grantDumpPermission() {
        Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
        Shell.SU.run("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
    }

    public void enterDoze(Context context) {
        if (!Utils.isDeviceDozing(context)) {
            Log.i(TAG, "Entering Doze");
            executeCommand("dumpsys deviceidle force-idle");
            dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("ENTER"));
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
        } else {
            Log.i(TAG, "enterDoze() received but skipping because device is already Dozing");
        }
    }

    public void exitDoze() {
        Log.i(TAG, "Exiting Doze");
        executeCommand("dumpsys deviceidle step");
        dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("EXIT"));
        saveDozeDataStats();
        enableSensorsRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Re-enabling motion sensors");
                executeCommand("dumpsys sensorservice enable");
                autoRotateBrightnessFix();
            }
        };
        if (!enableSensors) {
            localHandler.postDelayed(enableSensorsRunnable, 1000);
        }

    }

    public void executeCommand(final String command) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                List<String> output = Shell.SH.run(command);
                if (output != null) {
                    printShellOutput(output);
                } else {
                    Log.i(TAG, "Error occurred while executing command (" + command + ")");
                }
            }
        });
    }

    public void printShellOutput(List<String> output) {
        if (!output.isEmpty()) {
            for (String s : output) {
                Log.i(TAG, s);
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

    public void autoRotateBrightnessFix() {
        if (useAutoRotateAndBrightnessFix) {
            Log.i(TAG, "Executing auto-rotate fix by doing a toggle");
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoRotateEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoRotateEnabled(getApplicationContext())));
            Utils.setAutoRotateEnabled(getApplicationContext(), !Utils.isAutoRotateEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 200ms");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoRotateEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoRotateEnabled(getApplicationContext())));
            Utils.setAutoRotateEnabled(getApplicationContext(), !Utils.isAutoRotateEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 200ms");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Executing auto-brightness fix by doing a toggle");
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoBrightnessEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoBrightnessEnabled(getApplicationContext())));
            Utils.setAutoBrightnessEnabled(getApplicationContext(), !Utils.isAutoBrightnessEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 200ms");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoBrightnessEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoBrightnessEnabled(getApplicationContext())));
            Utils.setAutoBrightnessEnabled(getApplicationContext(), !Utils.isAutoBrightnessEnabled(getApplicationContext()));
        }
    }


    class ReloadSettingsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "User changed a setting, loading new settings into service");
            reloadSettings();
        }
    }

    class DozeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, Intent intent) {
            // This is to prevent Doze from kicking before the OS locks the screen
            int time = Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000);
            int delay = dozeEnterDelay * 60 * 1000;
            Log.i(TAG, "Doze delay: " + dozeEnterDelay + "ms");
            time = time + delay;
            enterDozeRunnable = new Runnable() {
                @Override
                public void run() {
                    enterDoze(context);
                }
            };
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.i(TAG, "Screen ON received");
                if (Utils.isDeviceDozing(context)) {
                    exitDoze();
                } else {
                    Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(time) + "ms has not passed or disableWhenCharging=true");
                    localHandler.removeCallbacks(enterDozeRunnable);
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.i(TAG, "Screen OFF received");
                if (Utils.isConnectedToCharger(getApplicationContext()) && disableWhenCharging) {
                    Log.i(TAG, "Connected to charger and disableWhenCharging=true, skip entering Doze");
                } else if (Utils.isUserInCommunicationCall(context)) {
                    Log.i(TAG, "User is in a phone call, VOIP call or an audio/video chat, skip entering Doze");
                } else {
                    Log.i(TAG, "Waiting for " + Integer.toString(time) + "ms and then entering Doze");
                    localHandler.postDelayed(enterDozeRunnable, time);
                }
            }
        }
    }

}

package com.suyashsrijan.forcedoze;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class ForceDozeService extends Service {

    boolean isSuAvailable = false;
    boolean disableWhenCharging = true;
    boolean enableSensors = false;
    boolean useAutoRotateAndBrightnessFix = false;
    boolean showPersistentNotif = false;
    int dozeEnterDelay = 0;
    Timer enterDozeTimer;
    Timer disableSensorsTimer;
    Timer enableSensorsTimer;
    DozeReceiver localDozeReceiver;
    ReloadSettingsReceiver reloadSettingsReceiver;
    private NotificationCompat.Builder mBuilder;
    Set<String> dozeUsageData;
    String sensorWhitelistPackage = "";
    Long timeEnterDoze;
    Long timeExitDoze;
    String lastScreenOff;
    public static String TAG = "ForceDozeService";

    public ForceDozeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localDozeReceiver = new DozeReceiver();
        reloadSettingsReceiver = new ReloadSettingsReceiver();
        enterDozeTimer = new Timer();
        enableSensorsTimer = new Timer();
        disableSensorsTimer = new Timer();
        mBuilder = new NotificationCompat.Builder(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        LocalBroadcastManager.getInstance(this).registerReceiver(reloadSettingsReceiver, new IntentFilter("reload-settings"));
        this.registerReceiver(localDozeReceiver, filter);
        dozeEnterDelay = getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        useAutoRotateAndBrightnessFix = getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
        sensorWhitelistPackage = getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        enableSensors = getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        disableWhenCharging = getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
        isSuAvailable = getDefaultSharedPreferences(getApplicationContext()).getBoolean("isSuAvailable", false);
        showPersistentNotif = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("showPersistentNotif", false);
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
        if (showPersistentNotif) {
            showPersistentNotification();
        }
        return START_STICKY;
    }

    public void reloadSettings() {
        dozeEnterDelay = getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        useAutoRotateAndBrightnessFix = getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
        sensorWhitelistPackage = getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        enableSensors = getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        disableWhenCharging = getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
        dozeUsageData = getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageData", new LinkedHashSet<String>());
        showPersistentNotif = getDefaultSharedPreferences(getApplicationContext()).getBoolean("showPersistentNotif", false);
    }

    public void grantDumpPermission() {
        Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
        Shell.SU.run("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
    }

    public void enterDoze(Context context) {
        if (!Utils.isDeviceDozing(context)) {
            if (!Utils.isScreenOn(context)) {
                timeEnterDoze = System.currentTimeMillis();
                Log.i(TAG, "Entering Doze");
                if (Utils.isDeviceRunningOnNPreview()) {
                    executeCommand("dumpsys deviceidle force-idle deep");
                } else {
                    executeCommand("dumpsys deviceidle force-idle");
                }
                lastScreenOff = Utils.getDateCurrentTimeZone(System.currentTimeMillis());

                dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("ENTER"));
                saveDozeDataStats();

                if (!enableSensors) {
                    disableSensorsTimer = new Timer();
                    disableSensorsTimer.schedule(new TimerTask() {
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
                    }, 2000);
                }
            } else {
                Log.i(TAG, "Screen is on, skip entering Doze");
            }
        } else {
            Log.i(TAG, "enterDoze() received but skipping because device is already Dozing");
        }
    }

    public void exitDoze() {
        timeExitDoze = System.currentTimeMillis();
        Log.i(TAG, "Exiting Doze");
        if (Utils.isDeviceRunningOnNPreview()) {
            executeCommand("dumpsys deviceidle unforce");
        } else {
            executeCommand("dumpsys deviceidle step");
        }
        dozeUsageData.add(Utils.getDateCurrentTimeZone(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("EXIT"));
        saveDozeDataStats();
        if (showPersistentNotif) {
            updatePersistentNotification(false, lastScreenOff, Utils.diffInMins(timeEnterDoze, timeExitDoze));
        }
        if (!enableSensors) {
            enableSensorsTimer = new Timer();
            enableSensorsTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(TAG, "Re-enabling motion sensors");
                    executeCommand("dumpsys sensorservice enable");
                    autoRotateBrightnessFix();
                }
            }, 2000);
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
        SharedPreferences sharedPreferences = getDefaultSharedPreferences(getApplicationContext());
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

    public void showPersistentNotification() {
        Notification n  = mBuilder
                .setContentTitle("ForceDoze")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Dozing: " + Boolean.toString(Utils.isDeviceDozing(getApplicationContext())) + "\nLast Screen off: " + "No data" + "\nTime spent dozing: " + "No data"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true).build();
        startForeground(1234, n);
    }

    public void updatePersistentNotification(boolean isDozing, String lastScreenOff, int timeSpentDozing) {
        Notification n  = mBuilder
                .setContentTitle("ForceDoze")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Dozing: " + Boolean.toString(isDozing) + "\nLast Screen off: " + lastScreenOff + "\nTime spent dozing: " + Integer.toString(timeSpentDozing) + "mins"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true).build();
        startForeground(1234, n);
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
            Log.i(TAG, "Doze delay: " + delay + "ms");
            time = time + delay;

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.i(TAG, "Screen ON received");
                if (Utils.isDeviceDozing(context)) {
                   exitDoze();
                } else {
                    Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(time) + "ms has not passed OR disableWhenCharging=true");
                    enterDozeTimer.cancel();
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.i(TAG, "Screen OFF received");
                if (Utils.isConnectedToCharger(getApplicationContext()) && disableWhenCharging) {
                    Log.i(TAG, "Connected to charger and disableWhenCharging=true, skip entering Doze");
                } else if (Utils.isUserInCommunicationCall(context)) {
                    Log.i(TAG, "User is in a VOIP call or an audio/video chat, skip entering Doze");
                } else if (Utils.isUserInCall(context)) {
                    Log.i(TAG, "User is in a phone call, skip entering Doze");
                } else {
                    Log.i(TAG, "Waiting for " + Integer.toString(time) + "ms and then entering Doze");
                    enterDozeTimer = new Timer();
                    enterDozeTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                              enterDoze(context);
                        }
                    }, time);
                }
            }
        }
    }

}

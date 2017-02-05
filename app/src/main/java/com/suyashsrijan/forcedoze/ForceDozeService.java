package com.suyashsrijan.forcedoze;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class ForceDozeService extends Service {

    private boolean isSuAvailable = false;
    private boolean disableWhenCharging = true;
    private boolean enableSensors = false;
    private boolean useAutoRotateAndBrightnessFix = false;
    private boolean showPersistentNotif = false;
    private boolean ignoreLockscreenTimeout = false;
    private boolean useXposedSensorWorkaround = false;
    private boolean useNonRootSensorWorkaround = false;
    private boolean turnOffWiFiInDoze = false;
    private boolean turnOffDataInDoze = false;
    private boolean wasWiFiTurnedOn = false;
    private boolean wasMobileDataTurnedOn = false;
    private boolean maintenance = false;
    private boolean setPendingDozeEnterAlarm = false;
    private int dozeEnterDelay = 0;
    private Timer enterDozeTimer;
    private Timer disableSensorsTimer;
    private Timer enableSensorsTimer;
    private DozeReceiver localDozeReceiver;
    private ReloadSettingsReceiver reloadSettingsReceiver;
    private PendingIntentDozeReceiver pendingIntentDozeReceiver;
    private NotificationCompat.Builder mBuilder;
    private PendingIntent reenterDozePendingIntent;
    private PowerManager pm;
    private AlarmManager alarmManager;
    private PowerManager.WakeLock tempWakeLock;
    private Set<String> dozeUsageData;
    private String sensorWhitelistPackage = "";
    private Long timeEnterDoze = 0L;
    private Long timeExitDoze = 0L;
    private String lastScreenOff = "Unknown";
    private int lastDozeEnterBatteryLife = 0;
    private int lastDozeExitBatteryLife = 0;
    private String TAG = "ForceDozeService";
    private String lastKnownState = "null";

    public ForceDozeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localDozeReceiver = new DozeReceiver();
        reloadSettingsReceiver = new ReloadSettingsReceiver();
        pendingIntentDozeReceiver = new PendingIntentDozeReceiver();
        enterDozeTimer = new Timer();
        enableSensorsTimer = new Timer();
        disableSensorsTimer = new Timer();
        mBuilder = new NotificationCompat.Builder(this);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(reloadSettingsReceiver, new IntentFilter("reload-settings"));
        LocalBroadcastManager.getInstance(this).registerReceiver(pendingIntentDozeReceiver, new IntentFilter("reenter-doze"));
        this.registerReceiver(localDozeReceiver, filter);
        turnOffDataInDoze = getDefaultSharedPreferences(getApplicationContext()).getBoolean("turnOffDataInDoze", false);
        turnOffWiFiInDoze = getDefaultSharedPreferences(getApplicationContext()).getBoolean("turnOffWiFiInDoze", false);
        ignoreLockscreenTimeout = getDefaultSharedPreferences(getApplicationContext()).getBoolean("ignoreLockscreenTimeout", true);
        useXposedSensorWorkaround = getDefaultSharedPreferences(getApplicationContext()).getBoolean("useXposedSensorWorkaround", false);
        useNonRootSensorWorkaround = getDefaultSharedPreferences(getApplicationContext()).getBoolean("useNonRootSensorWorkaround", false);
        dozeEnterDelay = getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        useAutoRotateAndBrightnessFix = getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
        sensorWhitelistPackage = getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        enableSensors = getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        disableWhenCharging = getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
        isSuAvailable = getDefaultSharedPreferences(getApplicationContext()).getBoolean("isSuAvailable", false);
        showPersistentNotif = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("showPersistentNotif", false);
        dozeUsageData = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageDataAdvanced", new LinkedHashSet<String>());

        if (!Utils.isDumpPermissionGranted(getApplicationContext())) {
            if (isSuAvailable) {
                grantDumpPermission();
            }
        }

        if (Utils.isDeviceRunningOnNPreview()) {
            if (!Utils.isDevicePowerPermissionGranted(getApplicationContext())) {
                if (isSuAvailable) {
                    grantDevicePowerPermission();
                }
            }
        }

        if (!Utils.isReadPhoneStatePermissionGranted(getApplicationContext())) {
            if (isSuAvailable) {
                grantReadPhoneStatePermission();
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pendingIntentDozeReceiver);
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
        addSelfToDozeWhitelist();
        lastKnownState = getDeviceIdleState();
        return START_STICKY;
    }

    public void reloadSettings() {
        Log.i(TAG, "ForceDoze settings reloaded ----------------------------------");
        dozeUsageData = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageDataAdvanced", new LinkedHashSet<String>());
        Log.i(TAG, "dozeUsageData: " + "Total Entries -> " + dozeUsageData.size());
        turnOffDataInDoze = getDefaultSharedPreferences(getApplicationContext()).getBoolean("turnOffDataInDoze", false);
        Log.i(TAG, "turnOffDataInDoze: " + turnOffDataInDoze);
        turnOffWiFiInDoze = getDefaultSharedPreferences(getApplicationContext()).getBoolean("turnOffWiFiInDoze", false);
        Log.i(TAG, "turnOffWiFiInDoze: " + turnOffWiFiInDoze);
        ignoreLockscreenTimeout = getDefaultSharedPreferences(getApplicationContext()).getBoolean("ignoreLockscreenTimeout", false);
        Log.i(TAG, "ignoreLockscreenTimeout: " + ignoreLockscreenTimeout);
        dozeEnterDelay = getDefaultSharedPreferences(getApplicationContext()).getInt("dozeEnterDelay", 0);
        Log.i(TAG, "dozeEnterDelay: " + dozeEnterDelay);
        useAutoRotateAndBrightnessFix = getDefaultSharedPreferences(getApplicationContext()).getBoolean("autoRotateAndBrightnessFix", false);
        Log.i(TAG, "useAutoRotateAndBrightnessFix: " + useAutoRotateAndBrightnessFix);
        sensorWhitelistPackage = getDefaultSharedPreferences(getApplicationContext()).getString("sensorWhitelistPackage", "");
        Log.i(TAG, "sensorWhitelistPackage: " + sensorWhitelistPackage);
        enableSensors = getDefaultSharedPreferences(getApplicationContext()).getBoolean("enableSensors", false);
        Log.i(TAG, "enableSensors: " + enableSensors);
        disableWhenCharging = getDefaultSharedPreferences(getApplicationContext()).getBoolean("disableWhenCharging", true);
        Log.i(TAG, "disableWhenCharging: " + disableWhenCharging);
        showPersistentNotif = getDefaultSharedPreferences(getApplicationContext()).getBoolean("showPersistentNotif", false);
        Log.i(TAG, "showPersistentNotif: " + showPersistentNotif);
        useXposedSensorWorkaround = getDefaultSharedPreferences(getApplicationContext()).getBoolean("useXposedSensorWorkaround", false);
        Log.i(TAG, "useXposedSensorWorkaround: " + useXposedSensorWorkaround);
        useNonRootSensorWorkaround = getDefaultSharedPreferences(getApplicationContext()).getBoolean("useNonRootSensorWorkaround", false);
        Log.i(TAG, "useNonRootSensorWorkaround: " + useNonRootSensorWorkaround);
        Log.i(TAG, "ForceDoze settings reloaded ----------------------------------");
    }

    public void grantDumpPermission() {
        Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
        executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
    }

    public void grantDevicePowerPermission() {
        Log.i(TAG, "Granting android.permission.DEVICE_POWER to com.suyashsrijan.forcedoze");
        executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DEVICE_POWER");
    }

    public void grantReadPhoneStatePermission() {
        Log.i(TAG, "Granting android.permission.READ_PHONE_STATE to com.suyashsrijan.forcedoze");
        executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.READ_PHONE_STATE");
    }

    public void addSelfToDozeWhitelist() {
        Log.i(TAG, "Adding service to Doze whitelist for stability");
        executeCommand("dumpsys deviceidle whitelist +com.suyashsrijan.forcedoze");
    }

    public void enterDoze(Context context) {
        if (!getDeviceIdleState().equals("IDLE")) {
            if (!Utils.isScreenOn(context)) {
                lastKnownState = "IDLE";
                if (tempWakeLock != null) {
                    if (tempWakeLock.isHeld()) {
                        Log.i(TAG, "Releasing ForceDozeTempWakelock");
                        tempWakeLock.release();
                    }
                }

                timeEnterDoze = System.currentTimeMillis();
                lastDozeEnterBatteryLife = Utils.getBatteryLevel2(getApplicationContext());
                Log.i(TAG, "Entering Doze");
                if (Utils.isDeviceRunningOnNPreview()) {
                    executeCommand("dumpsys deviceidle force-idle deep");
                } else {
                    executeCommand("dumpsys deviceidle force-idle");
                }
                lastScreenOff = Utils.getDateCurrentTimeZone(System.currentTimeMillis());

                dozeUsageData.add(Long.toString(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("ENTER"));
                saveDozeDataStats();

                if (!useXposedSensorWorkaround) {
                    if (!enableSensors) {
                        disableSensorsTimer = new Timer();
                        disableSensorsTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Log.i(TAG, "Disabling motion sensors");
                                if (sensorWhitelistPackage.equals("")) {
                                    executeCommand("dumpsys sensorservice restrict");
                                } else {
                                    Log.i(TAG, "Package " + sensorWhitelistPackage + " is whitelisted from sensorservice");
                                    Log.i(TAG, "Note: Packages that get whitelisted are supposed to request sensor access again, if the app doesn't work, email the dev of that app!");
                                    executeCommand("dumpsys sensorservice restrict " + sensorWhitelistPackage);
                                }
                            }
                        }, 2000);
                    } else {
                        Log.i(TAG, "Not disabling motion sensors because enableSensors=true");
                    }
                } else {
                    Log.i(TAG, "Xposed Sensor workaround selected, not disabling sensors");
                }

                if (turnOffWiFiInDoze) {
                    wasWiFiTurnedOn = Utils.isWiFiEnabled(context);
                    Log.i(TAG, "wasWiFiTurnedOn: " + wasWiFiTurnedOn);
                    if (wasWiFiTurnedOn) {
                        Log.i(TAG, "Disabling WiFi");
                        disableWiFi();
                    }

                }

                if (turnOffDataInDoze) {
                    wasMobileDataTurnedOn = Utils.isMobileDataEnabled(context);
                    Log.i(TAG, "wasDataTurnedOn: " + wasMobileDataTurnedOn);
                    if (wasMobileDataTurnedOn) {
                        Log.i(TAG, "Disabling mobile data");
                        disableMobileData();
                    }
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
        lastDozeExitBatteryLife = Utils.getBatteryLevel2(getApplicationContext());
        lastKnownState = "ACTIVE";
        if (Utils.isDeviceRunningOnNPreview()) {
            executeCommand("dumpsys deviceidle unforce");
        } else {
            executeCommand("dumpsys deviceidle step");
        }

        dozeUsageData.add(Long.toString(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("EXIT"));
        saveDozeDataStats();

        if (!useXposedSensorWorkaround) {
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

        if (useNonRootSensorWorkaround) {
            try {
                reenterDozePendingIntent.cancel();
                alarmManager.cancel(reenterDozePendingIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Timer updateNotif = new Timer();
        updateNotif.schedule(new TimerTask() {
            @Override
            public void run() {
                if (showPersistentNotif) {
                    updatePersistentNotification(lastScreenOff, Utils.diffInMins(timeEnterDoze, timeExitDoze), (lastDozeEnterBatteryLife - lastDozeExitBatteryLife));
                }
            }
        }, 2000);

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
        editor.remove("dozeUsageDataAdvanced");
        editor.commit();
        editor.putStringSet("dozeUsageDataAdvanced", dozeUsageData);
        editor.commit();
    }

    public void autoRotateBrightnessFix() {
        if (useAutoRotateAndBrightnessFix) {
            Log.i(TAG, "Executing auto-rotate fix by doing a toggle");
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoRotateEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoRotateEnabled(getApplicationContext())));
            Utils.setAutoRotateEnabled(getApplicationContext(), !Utils.isAutoRotateEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 100ms");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoRotateEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoRotateEnabled(getApplicationContext())));
            Utils.setAutoRotateEnabled(getApplicationContext(), !Utils.isAutoRotateEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 100ms");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Executing auto-brightness fix by doing a toggle");
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoBrightnessEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoBrightnessEnabled(getApplicationContext())));
            Utils.setAutoBrightnessEnabled(getApplicationContext(), !Utils.isAutoBrightnessEnabled(getApplicationContext()));
            try {
                Log.i(TAG, "Sleeping for 100ms");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            Log.i(TAG, "Current value: " + Boolean.toString(Utils.isAutoBrightnessEnabled(getApplicationContext())) + " to " + Boolean.toString(!Utils.isAutoBrightnessEnabled(getApplicationContext())));
            Utils.setAutoBrightnessEnabled(getApplicationContext(), !Utils.isAutoBrightnessEnabled(getApplicationContext()));
        }
    }

    public void showPersistentNotification() {
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, 0);
        Notification n = mBuilder
                .setContentTitle("ForceDoze")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Last Screen off: " + "No data" + "\nTime spent dozing: " + "No data" + "\nBattery usage in last Doze session: " + "No data"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(-2)
                .setContentIntent(intent)
                .setOngoing(true).build();
        startForeground(1234, n);
    }

    public void updatePersistentNotification(String lastScreenOff, int timeSpentDozing, int batteryUsage) {
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0,
                notificationIntent, 0);
        Notification n = mBuilder
                .setContentTitle("ForceDoze")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Last Screen off: " + lastScreenOff + "\nTime spent dozing: " + Integer.toString(timeSpentDozing) + "mins" + "\nBattery usage in last Doze session: " + batteryUsage + "%"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(-2)
                .setContentIntent(intent)
                .setOngoing(true).build();
        startForeground(1234, n);
    }

    public void setMobileNetwork(Context context, int targetState) {

        if (!Utils.isReadPhoneStatePermissionGranted(context)) {
            grantReadPhoneStatePermission();
        }

        String command;
        try {
            String transactionCode = getTransactionCode(context);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                SubscriptionManager mSubscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                for (int i = 0; i < mSubscriptionManager.getActiveSubscriptionInfoCountMax(); i++) {
                    if (transactionCode != null && transactionCode.length() > 0) {
                        int subscriptionId = mSubscriptionManager.getActiveSubscriptionInfoList().get(i).getSubscriptionId();
                        command = "service call phone " + transactionCode + " i32 " + subscriptionId + " i32 " + targetState;
                        List<String> output = Shell.SU.run(command);
                        if (output != null) {
                            for (String s : output) {
                                Log.i(TAG, s);
                            }
                        } else {
                            Log.i(TAG, "Error occurred while executing command (" + command + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "Failed to toggle mobile data: " + e.getMessage());
        }
    }

    private static String getTransactionCode(Context context) {
        try {
            final TelephonyManager mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final Class<?> mTelephonyClass = Class.forName(mTelephonyManager.getClass().getName());
            final Method mTelephonyMethod = mTelephonyClass.getDeclaredMethod("getITelephony");
            mTelephonyMethod.setAccessible(true);
            final Object mTelephonyStub = mTelephonyMethod.invoke(mTelephonyManager);
            final Class<?> mTelephonyStubClass = Class.forName(mTelephonyStub.getClass().getName());
            final Class<?> mClass = mTelephonyStubClass.getDeclaringClass();
            final Field field = mClass.getDeclaredField("TRANSACTION_setDataEnabled");
            field.setAccessible(true);
            return String.valueOf(field.getInt(null));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDeviceIdleState() {
        String state = "";
        List<String> output = Shell.SH.run("dumpsys deviceidle");
        String outputString = TextUtils.join(", ", output);
        if (outputString.contains("mState=ACTIVE")) {
            state = "ACTIVE";
        } else if (outputString.contains("mState=INACTIVE")) {
            state = "INACTIVE";
        } else if (outputString.contains("mState=IDLE_PENDING")) {
            state = "IDLE_PENDING";
        } else if (outputString.contains("mState=SENSING")) {
            state = "SENSING";
        } else if (outputString.contains("mState=LOCATING")) {
            state = "LOCATING";
        } else if (outputString.contains("mState=IDLE")) {
            state = "IDLE";
        } else if (outputString.contains("mState=IDLE_MAINTENANCE")) {
            state = "IDLE_MAINTENANCE";
        }
        return state;
    }


    public void disableMobileData() {
        setMobileNetwork(getApplicationContext(), 0);
    }

    public void enableMobileData() {
        setMobileNetwork(getApplicationContext(), 1);
    }

    public void disableWiFi() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
    }

    public void enableWiFi() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(true);
    }

    class ReloadSettingsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "User changed a setting, loading new settings into service");
            reloadSettings();
        }
    }

    class PendingIntentDozeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Pending intent broadcast received");
            setPendingDozeEnterAlarm = false;
            executeCommand("dumpsys deviceidle force-idle");
        }
    }

    class DozeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, Intent intent) {
            int time = Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000);
            if (time == 0) {
                time = 1000;
            }
            int delay = dozeEnterDelay * 60 * 1000;
            Log.i(TAG, "Doze delay: " + delay + "ms");
            time = time + delay;

            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.i(TAG, "Screen ON received");
                Log.i(TAG, "Last known Doze state: " + lastKnownState);
                Log.i(TAG, "Current Doze state: " + getDeviceIdleState());

                if (tempWakeLock != null) {
                    if (tempWakeLock.isHeld()) {
                        Log.i(TAG, "Releasing ForceDozeTempWakelock");
                        tempWakeLock.release();
                    }
                }

                if (turnOffWiFiInDoze) {
                    Log.i(TAG, "wasWiFiTurnedOn: " + wasWiFiTurnedOn);
                    if (wasWiFiTurnedOn) {
                        Log.i(TAG, "Enabling WiFi");
                        enableWiFi();
                        wasWiFiTurnedOn = false;
                    }

                }

                if (turnOffDataInDoze) {
                    Log.i(TAG, "wasDataTurnedOn: " + wasMobileDataTurnedOn);
                    if (wasMobileDataTurnedOn) {
                        Log.i(TAG, "Enabling mobile data");
                        enableMobileData();
                        wasMobileDataTurnedOn = false;
                    }
                }

                if (getDeviceIdleState().equals("IDLE") || getDeviceIdleState().equals("INACTIVE") || lastKnownState.equals("IDLE")) {
                    Log.i(TAG, "Exiting Doze");
                    exitDoze();
                } else {
                    if (ignoreLockscreenTimeout) {
                        Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(delay) + "ms has not passed OR disableWhenCharging=true");
                    } else {
                        Log.i(TAG, "Cancelling enterDoze() because user turned on screen and " + Integer.toString(time) + "ms has not passed OR disableWhenCharging=true");
                    }
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
                    if (ignoreLockscreenTimeout) {
                        if (dozeEnterDelay == 0) {
                            Log.i(TAG, "Ignoring lockscreen timeout value and entering Doze immediately");
                            enterDoze(context);
                        } else {
                            Log.i(TAG, "Waiting for " + Integer.toString(delay) + "ms and then entering Doze");
                            tempWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ForceDozeTempWakelock");
                            Log.i(TAG, "Acquiring temporary wakelock (ForceDozeTempWakelock)");
                            tempWakeLock.acquire();
                            enterDozeTimer = new Timer();
                            enterDozeTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    enterDoze(context);
                                }
                            }, delay);
                        }
                    } else {
                        Log.i(TAG, "Waiting for " + Integer.toString(time) + "ms and then entering Doze");
                        if (Utils.isLockscreenTimeoutValueTooHigh(getContentResolver())) {
                            tempWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ForceDozeTempWakelock");
                            Log.i(TAG, "Acquiring temporary wakelock (ForceDozeTempWakelock)");
                            tempWakeLock.acquire();
                        }
                        enterDozeTimer = new Timer();
                        enterDozeTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                enterDoze(context);
                            }
                        }, time);
                    }

                }
            } else if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                if ((getDeviceIdleState().equals("IDLE") || !Utils.isScreenOn(context)) && disableWhenCharging) {
                    Log.i(TAG, "Charger connected, exiting Doze mode");
                    enterDozeTimer.cancel();
                    exitDoze();
                }
            } else if (intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
                Log.i(TAG, "ACTION_DEVICE_IDLE_MODE_CHANGED received");
                lastKnownState = getDeviceIdleState();

                if (!Utils.isScreenOn(context) && getDeviceIdleState().equals("IDLE_MAINTENANCE")) {
                    if (!maintenance) {
                        Log.i(TAG, "Device exited Doze for maintenance");
                        dozeUsageData.add(Long.toString(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("EXIT_MAINTENANCE"));
                        saveDozeDataStats();

                        if (turnOffDataInDoze) {
                            if (wasMobileDataTurnedOn) {
                                Log.i(TAG, "Enabling mobile data");
                                enableMobileData();
                                wasMobileDataTurnedOn = false;
                            }
                        }
                        if (turnOffWiFiInDoze) {
                            if (wasWiFiTurnedOn) {
                                Log.i(TAG, "Enabling WiFi");
                                enableWiFi();
                                wasWiFiTurnedOn = false;
                            }
                        }

                        maintenance = true;
                    }
                } else if (!Utils.isScreenOn(context) && getDeviceIdleState().equals("IDLE")) {
                    if (maintenance) {
                        Log.i(TAG, "Device entered Doze after maintenance");
                        dozeUsageData.add(Long.toString(System.currentTimeMillis()).concat(",").concat(Float.toString(Utils.getBatteryLevel2(getApplicationContext()))).concat(",").concat("ENTER_MAINTENANCE"));
                        saveDozeDataStats();

                        if (turnOffWiFiInDoze) {
                            wasWiFiTurnedOn = Utils.isWiFiEnabled(context);
                            if (wasWiFiTurnedOn) {
                                Log.i(TAG, "Disabling WiFi");
                                disableWiFi();
                            }

                        }

                        if (turnOffDataInDoze) {
                            wasMobileDataTurnedOn = Utils.isMobileDataEnabled(context);
                            if (wasMobileDataTurnedOn) {
                                Log.i(TAG, "Disabling mobile data");
                                disableMobileData();
                            }
                        }

                        maintenance = false;
                    }
                }

                if (useNonRootSensorWorkaround) {
                    if (!setPendingDozeEnterAlarm) {
                        if (!Utils.isScreenOn(context) && (!getDeviceIdleState().equals("IDLE") || !getDeviceIdleState().equals("IDLE_MAINTENANCE"))) {
                            Log.i(TAG, "Device gone out of Doze, scheduling pendingIntent for enterDoze in 15 mins");
                            reenterDozePendingIntent = PendingIntent.getBroadcast(context, 1, new Intent(context, ReenterDoze.class), PendingIntent.FLAG_CANCEL_CURRENT);
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 900000, reenterDozePendingIntent);
                            setPendingDozeEnterAlarm = true;
                        }
                    }
                }
            }
        }
    }

}

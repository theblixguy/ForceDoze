package com.suyashsrijan.forcedoze;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Display;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class Utils {

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDumpPermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isReadPhoneStatePermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isReadLogsPermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isDevicePowerPermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission("android.permission.DEVICE_POWER") == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isConnectedToCharger(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return (batteryIntent != null && batteryIntent.getIntExtra("plugged", 0) != 0);
    }

    public static String getDateCurrentTimeZone(long timestamp) {
        //return DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.UK).format(new Date(timestamp));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss");
        return dateFormat.format(cal.getTime());
    }

    public static int getBatteryLevel2(Context context) {

        final Intent batteryIntent = context
                .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryIntent == null) {
            return Math.round(50.0f);
        }

        final int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level == -1 || scale == -1) {
            return Math.round(50.0f);
        }

        float battery_level = ((float) level / (float) scale) * 100.0f;
        return Math.round(battery_level);

    }

    public static boolean checkForAutoPowerModesFlag() {
        return Resources.getSystem().getBoolean(Resources.getSystem().getIdentifier("config_enableAutoPowerModes", "bool", "android"));
    }

    public static boolean isDeviceRunningOnNPreview() {
        return ("N".equals(Build.VERSION.CODENAME));
    }

    public static int diffInMins(long start, long end) {
        int diff = (int) ((end - start) / 1000) / 60;
        return diff;
    }

    public static void setAutoRotateEnabled(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
    }

    public static boolean isAutoRotateEnabled(Context context) {
        return android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    public static boolean isAutoBrightnessEnabled(Context context) {
        try {
            return android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    public static void setAutoBrightnessEnabled(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, enabled ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    public static boolean isUserInCommunicationCall(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return manager.getMode() == AudioManager.MODE_IN_CALL || manager.getMode() == AudioManager.MODE_IN_COMMUNICATION;
    }

    public static boolean isUserInCall(Context context) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK || manager.getCallState() == TelephonyManager.CALL_STATE_RINGING;
    }

    public static boolean isMobileDataEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), "mobile_data", 1) == 1;
    }

    public static boolean isWiFiEnabled(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }

    public static boolean isLockscreenTimeoutValueTooHigh(ContentResolver contentResolver) {
        return Settings.Secure.getInt(contentResolver, "lock_screen_lock_after_timeout", 5000) >= 5000;
    }

    public static float getLockscreenTimeoutValue(ContentResolver contentResolver) {
        return ((float) (Settings.Secure.getInt(contentResolver, "lock_screen_lock_after_timeout", 5000) / 1000f) / 60f);
    }

    public static boolean doesSettingExist(String settingName) {
        String[] updatableSettings = {"turnOffDataInDoze", "turnOffWiFiInDoze", "ignoreLockscreenTimeout",
                "dozeEnterDelay", "useAutoRotateAndBrightnessFix", "enableSensors", "disableWhenCharging",
                "showPersistentNotif", "useXposedSensorWorkaround", "useNonRootSensorWorkaround"};
        return Arrays.asList(updatableSettings).contains(settingName);
    }

    public static void updateSettingBool(Context context, String settingName, boolean settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(settingName, settingValue).apply();
    }

    public static void updateSettingInt(Context context, String settingName, int settingValue) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(settingName, settingValue).apply();
    }

    public static boolean isSettingBool(String settingName) {
        // Since all the settings loaded dynamically by the service except dozeEnterDelay are bools,
        // return true only if settingName != dozeEnterDelay
        if (settingName.equals("dozeEnterDelay")) {
            return false;
        } else return true;
    }

    public static boolean isXposedInstalled(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> applicationInfoList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo applicationInfo : applicationInfoList) {
            if (applicationInfo.packageName.equals("de.robv.android.xposed.installer")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isScreenOn(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() == Display.STATE_ON
                    || display.getState() == Display.STATE_UNKNOWN) {
                return true;
            }
        }
        return false;
    }
}

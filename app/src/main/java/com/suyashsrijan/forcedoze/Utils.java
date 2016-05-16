package com.suyashsrijan.forcedoze;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

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

    public static boolean isReadLogsPermissionGranted(Context context) {
        if (context.checkCallingOrSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public static boolean isConnectedToCharger(Context context) {
        BatteryManager mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return mBatteryManager.isCharging();
    }

    public static String getDateCurrentTimeZone(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.UK).format(new Date(timestamp));
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

        float battery_level = ((float)level / (float)scale) * 100.0f;
        return Math.round(battery_level);

    }

    public static boolean checkForAutoPowerModesFlag() {
        return Resources.getSystem().getBoolean(Resources.getSystem().getIdentifier("config_enableAutoPowerModes", "bool", "android"));
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

    public static boolean isDeviceDozing(Context context) {
        return ((PowerManager) context.getSystemService(Service.POWER_SERVICE)).isDeviceIdleMode();
    }

    public static boolean isUserInCommunicationCall(Context context){
        AudioManager manager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return manager.getMode() == AudioManager.MODE_IN_CALL || manager.getMode() == AudioManager.MODE_IN_COMMUNICATION;
    }

    public static boolean isScreenOn(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() == Display.STATE_ON
                    || display.getState () == Display.STATE_UNKNOWN) {
                return true;
            }
        }
        return false;
    }
}

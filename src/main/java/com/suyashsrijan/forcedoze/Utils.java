package com.suyashsrijan.forcedoze;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;

public class Utils {

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
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
}

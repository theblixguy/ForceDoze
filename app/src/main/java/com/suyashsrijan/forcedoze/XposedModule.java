package com.suyashsrijan.forcedoze;

import android.content.res.XResources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    XSharedPreferences prefs;
    boolean usePermanentDoze = false;
    boolean useXposedSensorWorkaround = false;
    boolean serviceEnabled = false;

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        XResources.setSystemWideReplacement("android", "bool", "config_enableAutoPowerModes", true);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (loadPackageParam.packageName.equals("android")) {
            prefs = new XSharedPreferences("com.suyashsrijan.forcedoze");
            usePermanentDoze = prefs.getBoolean("usePermanentDoze", false);
            useXposedSensorWorkaround = prefs.getBoolean("useXposedSensorWorkaround", false);
            serviceEnabled = prefs.getBoolean("serviceEnabled", false);
            XposedBridge.log("ForceDozeXposed: usePermanentDoze: " + usePermanentDoze + ", useXposedSensorWorkaround: " +
                    useXposedSensorWorkaround + ", serviceEnabled: " + serviceEnabled);
            if (useXposedSensorWorkaround && serviceEnabled) {
                XposedBridge.log("ForceDozeXposed: Hooking AnyMotionDetector");
                final Class AnyMotionDetector = XposedHelpers.findClass("com.android.server.AnyMotionDetector", loadPackageParam.classLoader);
                XposedHelpers.findAndHookMethod(AnyMotionDetector, "getStationaryStatus", XC_MethodReplacement.returnConstant(0));
                XposedBridge.log("ForceDozeXposed: Hooked AnyMotionDetector");
            }
        }
    }
}

package com.suyashsrijan.forcedoze;

import android.content.res.XResources;

import de.robv.android.xposed.IXposedHookZygoteInit;

public class DozeOnUnsupportedDevices implements IXposedHookZygoteInit {

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        XResources.setSystemWideReplacement("android", "bool", "config_enableAutoPowerModes", true);
    }

}

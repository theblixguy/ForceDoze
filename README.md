# ForceDoze
ForceDoze allows you to forcefully enable Doze right after you turn off your screen, and on top of that, it also disables motion sensors so Doze stays active even if your device is not stationary while screen off. Doze will only deactivate periodically to execute maintenance jobs (like getting notifications, etc), otherwise it will remain active as long as your screen is off. This brings a lot more battery savings than standard Doze functionality, because even with screen off and Doze enabled, Doze is still periodically checking for movement, and disabling motion sensing improves battery life further

# Features
* Force Doze mode immediatwly after screen off or after a user specified delay
* Add/remove apps or packages directly to system Doze whitelist
* Disable motion sensors to prevent Doze from kicking in during movement
* Tasker support to turn on/off ForceDoze and modify other features
* Disable WiFi and mobile data completely during Doze
* Enable Doze mode on devices where OEM has disabled it
* No root mode so you can enjoy the core benefits without rooting your device
* Free, no ads and open source

Play Store link: https://play.google.com/store/apps/details?id=com.suyashsrijan.forcedoze&hl=en

## Android
### Requirements for compiling source code:

* Android 6.0 (Marshmallow) SDK platform
* Android smartphone running 6.0 (Marshmallow)
* Android Studio
* Root (can work with limited functionality in non-root mode)
* Xposed (for extra functionality)

# License

This code is licensed under GPL v2

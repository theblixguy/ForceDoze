package com.suyashsrijan.forcedoze;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.widget.CompoundButton;

import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.io.IOException;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "ForceDoze";
    SharedPreferences settings;
    SharedPreferences.Editor editor;
    Boolean isSuAvailable = false;
    Boolean isDozeDisabled = false;
    Boolean serviceEnabled = false;
    Boolean isDumpPermGranted = false;
    SwitchCompat toggleForceDozeSwitch;
    SwitchCompat toggleDozeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        serviceEnabled = settings.getBoolean("serviceEnabled", false);
        isDozeDisabled = settings.getBoolean("isDozeDisabled", false);
        isSuAvailable = settings.getBoolean("isSuAvailable", false);
        toggleForceDozeSwitch = (SwitchCompat)findViewById(R.id.switch1);
        toggleDozeSwitch = (SwitchCompat)findViewById(R.id.switch2);
        isDumpPermGranted = isDumpPermissionGranted();
        toggleDozeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i(TAG, "Toggling Doze state, new value: " + Boolean.toString(isChecked));
                if (isChecked) {
                    if (!isDozeDisabled) {
                        toggleDoze(false);
                        isDozeDisabled = true;
                    }
                } else {
                    if (isDozeDisabled) {
                        toggleDoze(true);
                        isDozeDisabled = false;
                    }
                }
            }
        });

        toggleForceDozeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Enabling ForceDoze");
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", true);
                    editor.apply();
                    if(!isMyServiceRunning(ForceDozeService.class)) {
                        startService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                } else {
                    Log.i(TAG, "Disabling ForceDoze");
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", false);
                    editor.apply();
                    if (isMyServiceRunning(ForceDozeService.class)) {
                        stopService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                }
            }
        });

        if (isDumpPermGranted) {
            Log.i(TAG, "android.permission.DUMP already granted, skipping SU check");
            if (serviceEnabled) {
                toggleForceDozeSwitch.setChecked(true);
                if(!isMyServiceRunning(ForceDozeService.class)) {
                    Log.i(TAG, "Starting ForceDozeService");
                    startService(new Intent(this, ForceDozeService.class));
                } else {
                    Log.i(TAG, "Service already running");
                }
            } else {
                Log.i(TAG, "Service not enabled");
            }
        } else {
            Log.i(TAG, "Check if SU is available, and request SU permission if it is");
            Tasks.executeInBackground(MainActivity.this, new BackgroundWork<Boolean>() {
                @Override
                public Boolean doInBackground() throws Exception {
                    return Shell.SU.available();
                }
            }, new Completion<Boolean>() {
                @Override
                public void onSuccess(Context context, Boolean result) {
                    isSuAvailable = result;
                    Log.i(TAG, "SU available: " + Boolean.toString(result));
                    if (isSuAvailable) {
                        Log.i(TAG, "Phone is rooted and SU permission granted");
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", true);
                        editor.apply();
                        if (serviceEnabled) {
                            toggleForceDozeSwitch.setChecked(true);
                            if(!isMyServiceRunning(ForceDozeService.class)) {
                                Log.i(TAG, "Starting ForceDozeService");
                                startService(new Intent(context, ForceDozeService.class));
                            } else {
                                Log.i(TAG, "Service already running");
                            }
                        } else {
                            Log.i(TAG, "Service not enabled");
                        }
                    } else {
                        Log.i(TAG, "SU permission denied or not available");
                        toggleForceDozeSwitch.setChecked(false);
                        toggleForceDozeSwitch.setEnabled(false);
                        toggleDozeSwitch.setChecked(false);
                        toggleDozeSwitch.setEnabled(false);
                        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                        builder.setTitle("Error");
                        builder.setMessage("SU permission denied or not available! If you don't have root, " +
                                "press 'Root workaround' to get instructions on how to enable no-root mode");
                        builder.setPositiveButton("Close", null);
                        builder.setNegativeButton("Root workaround", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                                showRootWorkaroundInstructions();
                            }
                        });
                        builder.show();
                    }

                    if (isDozeDisabled) {
                        toggleDozeSwitch.setChecked(true);
                    } else {
                        toggleDozeSwitch.setChecked(false);
                    }
                }
                @Override
                public void onError(Context context, Exception e) {
                    Log.e(TAG, "Error querying SU: " + e.getMessage());
                }
            });
        }


    }
    public boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void showRootWorkaroundInstructions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("No-root workaround");
        builder.setMessage("If your device isn't rooted, you can manually grant the permission 'android.permission.DUMP' " +
                "to this app by executing the following ADB command from your PC:\n\n" + "\"adb -d shell pm grant com.suyashsrijan.forcedoze android.permission.DUMP\"\n\n" +
                "Once you have done, please close this app and start again and you will then be able to access the app properly.");
        builder.setPositiveButton("Okay", null);
        builder.show();
    }

    public boolean isDumpPermissionGranted() {
        if (getApplicationContext().checkCallingOrSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED)
            return true;
        else return false;
    }

    public void toggleDoze(boolean enable){
        if (enable) {
            executeCommand("dumpsys deviceidle enable");
            editor = settings.edit();
            editor.putBoolean("isDozeDisabled", false);
            editor.apply();
            isDozeDisabled = false;
        } else {
            if (isMyServiceRunning(ForceDozeService.class)) {
                stopService(new Intent(MainActivity.this, ForceDozeService.class));
            }
            executeCommand("dumpsys deviceidle enable");
            editor = settings.edit();
            editor.putBoolean("isDozeDisabled", true);
            editor.apply();
            isDozeDisabled = true;
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
}
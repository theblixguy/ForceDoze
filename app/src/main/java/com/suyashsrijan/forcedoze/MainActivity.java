package com.suyashsrijan.forcedoze;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.util.List;

import de.cketti.library.changelog.ChangeLog;
import eu.chainfire.libsuperuser.Shell;
import io.github.eliseomartelli.simplecustomtabs.CustomTabs;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "ForceDoze";
    SharedPreferences settings;
    SharedPreferences.Editor editor;
    Boolean isDozeEnabledByOEM = true;
    Boolean isSuAvailable = false;
    Boolean isDozeDisabled = false;
    Boolean serviceEnabled = false;
    Boolean isDumpPermGranted = false;
    SwitchCompat toggleForceDozeSwitch;
    MaterialDialog progressDialog = null;
    TextView textViewStatus;
    CoordinatorLayout coordinatorLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setElevation(0.0f);
        }
        CustomTabs.with(getApplicationContext()).warm();
        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        isDozeEnabledByOEM = Utils.checkForAutoPowerModesFlag();
        serviceEnabled = settings.getBoolean("serviceEnabled", false);
        isDozeDisabled = settings.getBoolean("isDozeDisabled", false);
        isSuAvailable = settings.getBoolean("isSuAvailable", false);
        toggleForceDozeSwitch = (SwitchCompat) findViewById(R.id.switch1);
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());
        textViewStatus = (TextView) findViewById(R.id.textView2);
        toggleForceDozeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", true);
                    editor.apply();
                    textViewStatus.setText("ForceDoze service is active");
                    if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                        Log.i(TAG, "Enabling ForceDoze");
                        startService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                } else {
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", false);
                    editor.apply();
                    textViewStatus.setText("ForceDoze service is inactive");
                    if (Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                        Log.i(TAG, "Disabling ForceDoze");
                        stopService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                }
            }
        });

        if (isDumpPermGranted) {
            Log.i(TAG, "android.permission.DUMP already granted, skipping SU check");
            if (serviceEnabled) {
                toggleForceDozeSwitch.setChecked(true);
                textViewStatus.setText("ForceDoze service is active");
                if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                    Log.i(TAG, "Starting ForceDozeService");
                    startService(new Intent(this, ForceDozeService.class));
                } else {
                    Log.i(TAG, "Service already running");
                }
            } else {
                textViewStatus.setText("ForceDoze service is inactive");
                Log.i(TAG, "Service not enabled");
            }
            ChangeLog cl = new ChangeLog(this);
            if (cl.isFirstRun()) {
                cl.getFullLogDialog().show();
            }
        } else {
            progressDialog = new MaterialDialog.Builder(this)
                    .title("Please wait")
                    .autoDismiss(false)
                    .cancelable(false)
                    .content("Requesting SU access...")
                    .progress(true, 0)
                    .show();
            Log.i(TAG, "Check if SU is available, and request SU permission if it is");
            Tasks.executeInBackground(MainActivity.this, new BackgroundWork<Boolean>() {
                @Override
                public Boolean doInBackground() throws Exception {
                    return Shell.SU.available();
                }
            }, new Completion<Boolean>() {
                @Override
                public void onSuccess(Context context, Boolean result) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    isSuAvailable = result;
                    Log.i(TAG, "SU available: " + Boolean.toString(result));
                    if (isSuAvailable) {
                        Log.i(TAG, "Phone is rooted and SU permission granted");
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", true);
                        editor.apply();
                        if (!Utils.isDumpPermissionGranted(getApplicationContext())) {
                            if (isSuAvailable) {
                                Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
                                executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
                            }
                        }
                        if (!Utils.isDevicePowerPermissionGranted(getApplicationContext())) {
                            if (Utils.isDeviceRunningOnNPreview()) {
                                executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DEVICE_POWER");
                            }
                        }
                        if (serviceEnabled) {
                            toggleForceDozeSwitch.setChecked(true);
                            if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
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
                        textViewStatus.setText("ForceDoze service is disabled");
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", false);
                        editor.apply();
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

                    ChangeLog cl = new ChangeLog(MainActivity.this);
                    if (cl.isFirstRun()) {
                        cl.getFullLogDialog().show();
                    }
                }

                @Override
                public void onError(Context context, Exception e) {
                    Log.e(TAG, "Error querying SU: " + e.getMessage());
                }
            });
        }

        if (Utils.isLockscreenTimeoutValueTooHigh(getContentResolver())) {
            coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
            Snackbar.make(coordinatorLayout, "The lockscreen timeout value on your device is too high!", Snackbar.LENGTH_INDEFINITE)
                    .setAction("More info", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showLockScreenTimeoutInfoDialog();
                        }
                    })
                    .setActionTextColor(Color.RED)
                    .show();
        }

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());

        if (isDozeEnabledByOEM) {
            menu.getItem(2).setVisible(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_toggle_doze:
                showEnableDozeOnUnsupportedDeviceDialog();
                break;
            case R.id.action_donate_dev:
                CustomTabs.with(getApplicationContext())
                        .setStyle(new CustomTabs.Style(getApplicationContext())
                                .setShowTitle(true)
                                .setExitAnimation(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                                .setToolbarColor(R.color.colorPrimary))
                        .openUrl("https://www.paypal.me/suyashsrijan", this);
                break;
            case R.id.action_doze_batterystats:
                startActivity(new Intent(MainActivity.this, DozeStatsActivity.class));
                break;
            case R.id.action_app_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.action_doze_more_info:
                showMoreInfoDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showEnableDozeOnUnsupportedDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("Enable Doze on unsupported device (experimental)");
        builder.setMessage("Some devices have Doze mode disabled by the OEM. " +
                "This option can enable Doze mode on devices which do not have it enabled by default.\n\nNote: You need to turn " +
                "on the ForceDoze module in Xposed in order to permanently enable Doze. If you don't have Xposed installed, you will " +
                "have to turn this option on every time you restart your phone.");
        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.setNegativeButton("Enable Doze mode", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                executeCommand("setprop persist.sys.doze_powersave true");
                if (Utils.isDeviceRunningOnNPreview()) {
                    executeCommand("dumpsys deviceidle disable all");
                    executeCommand("dumpsys deviceidle enable all");
                } else {
                    executeCommand("dumpsys deviceidle disable");
                    executeCommand("dumpsys deviceidle enable");
                }
                if (Utils.isXposedInstalled(getApplicationContext())) {
                    showEnableXposedModuleDialog();
                }
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void showEnableXposedModuleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("Xposed detected");
        builder.setMessage("ForceDoze has detected that Xposed is installed on your device. In order to make Doze permanently " +
                "enabled on your device, you need to turn on the ForceDoze module in Xposed and restart your device. If you don't turn " +
                "on the module, you will lose Doze functionality on device restart and will have to manually enable Doze again. ");
        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void showRootWorkaroundInstructions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("No-root workaround");
        builder.setMessage("If your device isn't rooted, you can manually grant the permission 'android.permission.DUMP' " +
                "to this app by executing the following ADB command from your PC (the command is one-line, not separated):\n\n" + "\"adb -d shell pm grant com.suyashsrijan.forcedoze android.permission.DUMP\"\n\n" +
                "Once you have done, please close this app and start again and you will then be able to access the app properly.");
        builder.setPositiveButton("Okay", null);
        builder.setNegativeButton("Share command", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, "adb -d shell pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
                sendIntent.setType("text/plain");
                startActivity(sendIntent);

            }
        });
        builder.show();
    }

    public void showLockScreenTimeoutInfoDialog() {
        float lockscreenTimeout = Utils.getLockscreenTimeoutValue(getContentResolver());
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("Lockscreen timeout");
        builder.setMessage("The lockscreen timeout value on your device is too high (" + lockscreenTimeout + " mins). This means your device does not " +
                "automatically lock itself after the screen turns off until " + lockscreenTimeout + " mins have passed. This also means ForceDoze will wait " +
                "for " + lockscreenTimeout + " mins before making your device enter Doze mode by using a temporary wake lock. If you want ForceDoze to make " +
                "the device enter Doze mode faster, consider reducing the lockscreen timeout to Immediately in your device's Security Settings.");
        builder.setPositiveButton("Okay", null);
        builder.setNegativeButton("Open Security Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent securitySettingsIntent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                startActivity(securitySettingsIntent);

            }
        });
        builder.show();
    }

    public void showMoreInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle("More info");
        builder.setMessage("How Doze mode works on your device:\n\nIf a user leaves a device unplugged and stationary for a period of time (30 mins), with the screen off, " +
                "the device enters Doze mode. In Doze mode, the system attempts to conserve battery by restricting apps' " +
                "access to network and CPU-intensive services. It also prevents apps from accessing the network and defers " +
                "their jobs, syncs, and standard alarms\n\nPeriodically, the system exits Doze for a brief time to let apps complete " +
                "their deferred activities. During this maintenance window, the system runs all pending syncs, jobs, and alarms, and " +
                "lets apps access the network\n\nAt the conclusion of each maintenance window, the system again enters Doze, suspending " +
                "network access and deferring jobs, syncs, and alarms. Over time, the system schedules maintenance windows less and less " +
                "frequently, helping to reduce battery consumption in cases of longer-term inactivity when the device is not connected " +
                "to a charger.\n\nAs soon as the user wakes the device by moving it, turning on the screen, or connecting a charger, " +
                "the system exits Doze and all apps return to normal activity\n\nHow ForceDoze works:\n\nForceDoze makes the device enter Doze mode immediately " +
                "after screen off (or after a user specified delay), instead of waiting for 30 mins for the device to become stationary. On top of that, ForceDoze " +
                "also turns of the device's motion sensors, so Doze doesn't deactivate if you move your device. Doze will only deactivate during a maintenance window " +
                "(as explained above) or when you turn on your screen, which means your device will stay in Doze mode for a much longer time even if the device's screen " +
                "is off and the device is not stationary, which means the battery savings will be a lot higher than normal Doze.");
        builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void executeCommand(final String command) {
        if (isSuAvailable) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SU.run(command);
                    if (output != null) {
                        printShellOutput(output);
                    } else {
                        Log.i(TAG, "Error occurred while executing command (" + command + ")");
                    }
                }
            });
        } else {
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
    }

    public void printShellOutput(List<String> output) {
        if (!output.isEmpty()) {
            for (String s : output) {
                Log.i(TAG, s);
            }
        }
    }
}
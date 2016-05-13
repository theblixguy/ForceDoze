package com.suyashsrijan.forcedoze;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.util.List;

import de.cketti.library.changelog.ChangeLog;
import eu.chainfire.libsuperuser.Shell;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        isDozeEnabledByOEM = Utils.checkForAutoPowerModesFlag();
        serviceEnabled = settings.getBoolean("serviceEnabled", false);
        isDozeDisabled = settings.getBoolean("isDozeDisabled", false);
        isSuAvailable = settings.getBoolean("isSuAvailable", false);
        toggleForceDozeSwitch = (SwitchCompat) findViewById(R.id.switch1);
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());

        toggleForceDozeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Enabling ForceDoze");
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", true);
                    editor.apply();
                    if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                        startService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                } else {
                    Log.i(TAG, "Disabling ForceDoze");
                    editor = settings.edit();
                    editor.putBoolean("serviceEnabled", false);
                    editor.apply();
                    if (Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                        stopService(new Intent(MainActivity.this, ForceDozeService.class));
                    }
                }
            }
        });

        if (isDumpPermGranted) {
            Log.i(TAG, "android.permission.DUMP already granted, skipping SU check");
            if (serviceEnabled) {
                toggleForceDozeSwitch.setChecked(true);
                if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                    Log.i(TAG, "Starting ForceDozeService");
                    startService(new Intent(this, ForceDozeService.class));
                } else {
                    Log.i(TAG, "Service already running");
                }
            } else {
                Log.i(TAG, "Service not enabled");
            }
        } else {
            progressDialog = new MaterialDialog.Builder(this)
                    .title("Please wait")
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
                        progressDialog.cancel();
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
                }

                @Override
                public void onError(Context context, Exception e) {
                    Log.e(TAG, "Error querying SU: " + e.getMessage());
                }
            });

            ChangeLog cl = new ChangeLog(this);
            if (cl.isFirstRun()) {
                cl.getFullLogDialog().show();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());

        if (isDozeEnabledByOEM) {
            menu.getItem(2).setVisible(false);
        }
        if (!isDumpPermGranted) {
            menu.getItem(0).setEnabled(false);
            menu.getItem(2).setEnabled(false);
            menu.getItem(3).setEnabled(false);
        } else {
            menu.getItem(0).setEnabled(true);
            menu.getItem(2).setEnabled(true);
            menu.getItem(3).setEnabled(true);
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
                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
                builder.setTitle("Enable Doze on unsupported device (experimental)");
                builder.setMessage("Some devices have Doze mode disabled by the OEM. " +
                        "This option can enable Doze mode on devices which do not have it enabled by default.");
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
                        executeCommand("dumpsys deviceidle disable");
                        executeCommand("dumpsys deviceidle enable");
                        Toast.makeText(MainActivity.this, "Please restart your device now!", Toast.LENGTH_LONG).show();
                        dialogInterface.dismiss();
                    }
                });
                builder.show();
                break;
            case R.id.action_donate_dev:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/suyashsrijan")));
                break;
            case R.id.action_doze_batterystats:
                startActivity(new Intent(MainActivity.this, DozeBatteryConsumption.class));
                break;
            case R.id.action_app_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
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

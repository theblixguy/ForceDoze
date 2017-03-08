package com.suyashsrijan.forcedoze;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.jakewharton.processphoenix.ProcessPhoenix;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends AppCompatActivity {
    public static String TAG = "ForceDoze";
    static MaterialDialog progressDialog1 = null;
    static boolean isSuAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Utils.isMyServiceRunning(ForceDozeService.class, SettingsActivity.this)) {
            Intent intent = new Intent("reload-settings");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("preferenceScreen");
            PreferenceCategory xposedSettings = (PreferenceCategory) findPreference("xposedSettings");
            PreferenceCategory mainSettings = (PreferenceCategory) findPreference("mainSettings");
            PreferenceCategory dozeSettings = (PreferenceCategory) findPreference("dozeSettings");
            Preference resetForceDozePref = (Preference) findPreference("resetForceDoze");
            Preference debugLogPref = (Preference) findPreference("debugLogs");
            Preference clearDozeStats = (Preference) findPreference("resetDozeStats");
            Preference dozeDelay = (Preference) findPreference("dozeEnterDelay");
            Preference usePermanentDoze = (Preference) findPreference("usePermanentDoze");
            Preference dozeNotificationBlocklist = (Preference) findPreference("blacklistAppNotifications");
            Preference dozeAppBlocklist = (Preference) findPreference("blacklistApps");
            final Preference xposedSensorWorkaround = (Preference) findPreference("useXposedSensorWorkaround");
            final Preference nonRootSensorWorkaround = (Preference) findPreference("useNonRootSensorWorkaround");
            final Preference enableSensors = (Preference) findPreference("enableSensors");
            Preference turnOffDataInDoze = (Preference) findPreference("turnOffDataInDoze");
            final Preference autoRotateBrightnessFix = (Preference) findPreference("autoRotateAndBrightnessFix");
            CheckBoxPreference autoRotateFixPref = (CheckBoxPreference) findPreference("autoRotateAndBrightnessFix");

            resetForceDozePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                    builder.setTitle(getString(R.string.forcedoze_reset_initial_dialog_title));
                    builder.setMessage(getString(R.string.forcedoze_reset_initial_dialog_text));
                    builder.setPositiveButton(getString(R.string.yes_button_text), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            resetForceDoze();
                        }
                    });
                    builder.setNegativeButton(getString(R.string.no_button_text), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });
                    builder.show();
                    return true;
                }
            });

            dozeDelay.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    int delay = (int) o;
                    if (delay >= 5) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                        builder.setTitle(getString(R.string.doze_delay_warning_dialog_title));
                        builder.setMessage(getString(R.string.doze_delay_warning_dialog_text));
                        builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        });
                        builder.show();
                    }
                    return true;
                }
            });

            autoRotateFixPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (!Utils.isWriteSettingsPermissionGranted(getActivity())) {
                        requestWriteSettingsPermission();
                        return false;
                    } else return true;
                }
            });

            debugLogPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    progressDialog1 = new MaterialDialog.Builder(getActivity())
                            .title(getString(R.string.please_wait_text))
                            .cancelable(false)
                            .autoDismiss(false)
                            .content(getString(R.string.requesting_su_access_text))
                            .progress(true, 0)
                            .show();
                    Log.i(TAG, "Check if SU is available, and request SU permission if it is");
                    Tasks.executeInBackground(getActivity(), new BackgroundWork<Boolean>() {
                        @Override
                        public Boolean doInBackground() throws Exception {
                            return Shell.SU.available();
                        }
                    }, new Completion<Boolean>() {
                        @Override
                        public void onSuccess(Context context, Boolean result) {
                            if (progressDialog1 != null) {
                                progressDialog1.dismiss();
                            }
                            isSuAvailable = result;
                            Log.i(TAG, "SU available: " + Boolean.toString(result));
                            if (isSuAvailable) {
                                Log.i(TAG, "Phone is rooted and SU permission granted");
                                startActivity(new Intent(getActivity(), LogActivity.class));
                            } else {
                                Log.i(TAG, "SU permission denied or not available");
                                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                builder.setTitle(getString(R.string.error_text));
                                builder.setMessage(getString(R.string.su_perm_denied_msg));
                                builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
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
                    return true;
                }
            });

            clearDozeStats.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    progressDialog1 = new MaterialDialog.Builder(getActivity())
                            .title(getString(R.string.please_wait_text))
                            .cancelable(false)
                            .autoDismiss(false)
                            .content(getString(R.string.clearing_doze_stats_text))
                            .progress(true, 0)
                            .show();
                    Tasks.executeInBackground(getActivity(), new BackgroundWork<Boolean>() {
                        @Override
                        public Boolean doInBackground() throws Exception {
                            Log.i(TAG, "Clearing Doze stats");
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove("dozeUsageDataAdvanced");
                            return editor.commit();
                        }
                    }, new Completion<Boolean>() {
                        @Override
                        public void onSuccess(Context context, Boolean result) {
                            if (progressDialog1 != null) {
                                progressDialog1.dismiss();
                            }
                            if (result) {
                                Log.i(TAG, "Doze stats successfully cleared");
                                if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
                                    Intent intent = new Intent("reload-settings");
                                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                builder.setTitle(getString(R.string.cleared_text));
                                builder.setMessage(getString(R.string.doze_battery_stats_clear_msg));
                                builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                });
                                builder.show();
                            }

                        }

                        @Override
                        public void onError(Context context, Exception e) {
                            Log.e(TAG, "Error clearing Doze stats: " + e.getMessage());

                        }
                    });
                    return true;
                }
            });

            xposedSensorWorkaround.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final boolean newValue = (boolean) o;
                    progressDialog1 = new MaterialDialog.Builder(getActivity())
                            .title(getString(R.string.please_wait_text))
                            .cancelable(false)
                            .autoDismiss(false)
                            .content(getString(R.string.requesting_su_access_text))
                            .progress(true, 0)
                            .show();
                    Log.i(TAG, "Check if SU is available, and request SU permission if it is");
                    Tasks.executeInBackground(getActivity(), new BackgroundWork<Boolean>() {
                        @Override
                        public Boolean doInBackground() throws Exception {
                            return Shell.SU.available();
                        }
                    }, new Completion<Boolean>() {
                        @Override
                        public void onSuccess(Context context, Boolean result) {
                            if (progressDialog1 != null) {
                                progressDialog1.dismiss();
                            }
                            isSuAvailable = result;
                            Log.i(TAG, "SU available: " + Boolean.toString(result));
                            if (isSuAvailable) {
                                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                if (newValue) {
                                    editor.putBoolean("enableSensors", true);
                                    editor.putBoolean("useNonRootSensorWorkaround", false);
                                    editor.apply();
                                    enableSensors.setEnabled(false);
                                    autoRotateBrightnessFix.setEnabled(false);
                                    nonRootSensorWorkaround.setEnabled(false);
                                } else {
                                    enableSensors.setEnabled(true);
                                    autoRotateBrightnessFix.setEnabled(true);
                                    nonRootSensorWorkaround.setEnabled(true);
                                }
                                Log.i(TAG, "Phone is rooted and SU permission granted");
                                executeCommand("chmod 664 /data/data/com.suyashsrijan.forcedoze/shared_prefs/com.suyashsrijan.forcedoze_preferences.xml");
                                executeCommand("chmod 755 /data/data/com.suyashsrijan.forcedoze/shared_prefs");
                                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                builder.setTitle(getString(R.string.reboot_required_dialog_title));
                                builder.setMessage(getString(R.string.reboot_required_dialog_text));
                                builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                });
                                builder.show();

                            } else {
                                Log.i(TAG, "SU permission denied or not available");
                                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                builder.setTitle(getString(R.string.error_text));
                                builder.setMessage(getString(R.string.su_perm_denied_msg));
                                builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
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
                    return true;
                }
            });

            nonRootSensorWorkaround.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean newValue = (boolean) o;
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    if (newValue) {
                        editor.putBoolean("enableSensors", true);
                        editor.putBoolean("useXposedSensorWorkaround", false);
                        editor.apply();
                        xposedSensorWorkaround.setEnabled(false);
                        autoRotateBrightnessFix.setEnabled(false);
                        enableSensors.setEnabled(false);
                    } else {
                        xposedSensorWorkaround.setEnabled(true);
                        autoRotateBrightnessFix.setEnabled(true);
                        enableSensors.setEnabled(true);
                    }
                    return true;
                }
            });

            turnOffDataInDoze.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final boolean newValue = (boolean) o;
                    if (!newValue) {
                        return true;
                    } else {
                        progressDialog1 = new MaterialDialog.Builder(getActivity())
                                .title(getString(R.string.please_wait_text))
                                .cancelable(false)
                                .autoDismiss(false)
                                .content(getString(R.string.requesting_su_access_text))
                                .progress(true, 0)
                                .show();
                        Log.i(TAG, "Check if SU is available, and request SU permission if it is");
                        Tasks.executeInBackground(getActivity(), new BackgroundWork<Boolean>() {
                            @Override
                            public Boolean doInBackground() throws Exception {
                                return Shell.SU.available();
                            }
                        }, new Completion<Boolean>() {
                            @Override
                            public void onSuccess(Context context, Boolean result) {
                                if (progressDialog1 != null) {
                                    progressDialog1.dismiss();
                                }
                                isSuAvailable = result;
                                Log.i(TAG, "SU available: " + Boolean.toString(result));
                                if (isSuAvailable) {
                                    Log.i(TAG, "Phone is rooted and SU permission granted");
                                    Log.i(TAG, "Granting android.permission.READ_PHONE_STATE to com.suyashsrijan.forcedoze");
                                    executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.READ_PHONE_STATE");
                                } else {
                                    Log.i(TAG, "SU permission denied or not available");
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                    builder.setTitle(getString(R.string.error_text));
                                    builder.setMessage(getString(R.string.su_perm_denied_msg));
                                    builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
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

                        return true;
                    }
                }
            });

            if (!Utils.isXposedInstalled(getContext())) {
                preferenceScreen.removePreference(xposedSettings);
            }

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isSuAvailable = sharedPreferences.getBoolean("isSuAvailable", false);

            if (sharedPreferences.getBoolean("useXposedSensorWorkaround", false)) {
                enableSensors.setEnabled(false);
                autoRotateBrightnessFix.setEnabled(false);
                nonRootSensorWorkaround.setEnabled(false);
                sharedPreferences.edit().putBoolean("autoRotateAndBrightnessFix", false).apply();
                sharedPreferences.edit().putBoolean("enableSensors", false).apply();
                sharedPreferences.edit().putBoolean("useNonRootSensorWorkaround", false).apply();
            }

            if (Utils.isXposedInstalled(getContext())) {
                if (Utils.checkForAutoPowerModesFlag()) {
                    usePermanentDoze.setEnabled(false);
                    usePermanentDoze.setSummary(R.string.device_supports_doze_text);
                }
            }

            if (sharedPreferences.getBoolean("useNonRootSensorWorkaround", false)) {
                xposedSensorWorkaround.setEnabled(false);
                autoRotateBrightnessFix.setEnabled(false);
                enableSensors.setEnabled(false);
                sharedPreferences.edit().putBoolean("autoRotateAndBrightnessFix", false).apply();
                sharedPreferences.edit().putBoolean("enableSensors", false).apply();
                sharedPreferences.edit().putBoolean("useXposedSensorWorkaround", false).apply();
            }

            if (!isSuAvailable) {
                turnOffDataInDoze.setEnabled(false);
                turnOffDataInDoze.setSummary(getString(R.string.root_required_text));
                dozeNotificationBlocklist.setEnabled(false);
                dozeNotificationBlocklist.setSummary(getString(R.string.root_required_text));
                dozeAppBlocklist.setEnabled(false);
                dozeAppBlocklist.setSummary(getString(R.string.root_required_text));
            }

        }

        public void requestWriteSettingsPermission() {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle(getString(R.string.auto_rotate_brightness_fix_dialog_title));
            builder.setMessage(getString(R.string.auto_rotate_brightness_fix_dialog_text));
            builder.setPositiveButton(getString(R.string.authorize_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(getString(R.string.deny_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();
        }

        public void resetForceDoze() {
            Log.i(TAG, "Starting ForceDoze reset procedure");
            if (Utils.isMyServiceRunning(ForceDozeService.class, getActivity())) {
                Log.i(TAG, "Stopping ForceDozeService");
                getActivity().stopService(new Intent(getActivity(), ForceDozeService.class));
            }
            Log.i(TAG, "Enabling sensors, just in case they are disabled");
            executeCommand("dumpsys sensorservice enable");
            Log.i(TAG, "Disabling and re-enabling Doze mode");
            if (Utils.isDeviceRunningOnN()) {
                executeCommand("dumpsys deviceidle disable all");
                executeCommand("dumpsys deviceidle enable all");
            } else {
                executeCommand("dumpsys deviceidle disable");
                executeCommand("dumpsys deviceidle enable");
            }
            Log.i(TAG, "Resetting app preferences");
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().clear().apply();
            Log.i(TAG, "Trying to revoke android.permission.DUMP");
            executeCommand("pm revoke com.suyashsrijan.forcedoze android.permission.DUMP");
            executeCommand("pm revoke com.suyashsrijan.forcedoze android.permission.READ_LOGS");
            executeCommand("pm revoke com.suyashsrijan.forcedoze android.permission.READ_PHONE_STATE");
            executeCommand("pm revoke com.suyashsrijan.forcedoze android.permission.WRITE_SECURE_SETTINGS");
            executeCommand("pm revoke com.suyashsrijan.forcedoze android.permission.WRITE_SETTINGS");
            Log.i(TAG, "ForceDoze reset procedure complete");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle(getString(R.string.reset_complete_dialog_title));
            builder.setMessage(getString(R.string.reset_complete_dialog_text));
            builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    ProcessPhoenix.triggerRebirth(getActivity());
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
}

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

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.prefs);
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("preferenceScreen");
            PreferenceCategory xposedSettings = (PreferenceCategory) findPreference("xposedSettings");
            Preference resetForceDozePref = (Preference) findPreference("resetForceDoze");
            Preference debugLogPref = (Preference) findPreference("debugLogs");
            Preference clearDozeStats = (Preference) findPreference("resetDozeStats");
            Preference dozeDelay = (Preference) findPreference("dozeEnterDelay");
            Preference usePermanentDoze = (Preference) findPreference("usePermanentDoze");
            CheckBoxPreference autoRotateFixPref = (CheckBoxPreference) findPreference("autoRotateAndBrightnessFix");
            resetForceDozePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                    builder.setTitle("Do you want to reset?");
                    builder.setMessage("Resetting ForceDoze will make the following changes: \n\n1) Stop " +
                            "ForceDoze service\n2) Re-enable sensors, just in case they are disabled\n3) Disable and " +
                            "re-enable Doze mode, to ensure Doze mode is turned on properly\n4) Reset app preferences\n5) " +
                            "Revoke DUMP and READ_LOGS (if granted) permission\n\nDo you want to continue?");
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            resetForceDoze();
                        }
                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
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
                        builder.setTitle("Warning");
                        builder.setMessage("Doze Delay value is currently too high and may have a negative effect on battery life. Make sure " +
                                "to test the effects of the delay on battery life and consider reducing the delay if there is a noticeable drop in battery life.");
                        builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
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
                    if (!Settings.System.canWrite(getActivity())) {
                        requestWriteSettingsPermission();
                        return false;
                    } else return true;
                }
            });

            debugLogPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    progressDialog1 = new MaterialDialog.Builder(getActivity())
                            .title("Please wait")
                            .cancelable(false)
                            .autoDismiss(false)
                            .content("Requesting SU access...")
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
                                builder.setTitle("Error");
                                builder.setMessage("SU permission denied or not available!");
                                builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
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
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.remove("dozeUsageData");
                    editor.apply();
                    if (Utils.isMyServiceRunning(ForceDozeService.class, getActivity())) {
                        Intent intent = new Intent("reload-settings");
                        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                    builder.setTitle("Cleared");
                    builder.setMessage("Doze battery stats was successfully cleared!");
                    builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });
                    builder.show();
                    return true;
                }
            });

            if (!Utils.isXposedInstalled(getContext())) {
                preferenceScreen.removePreference(xposedSettings);
            }

            if (Utils.isXposedInstalled(getContext())) {
                if (Utils.checkForAutoPowerModesFlag()) {
                    usePermanentDoze.setEnabled(false);
                    usePermanentDoze.setSummary("Your device supports Doze");
                }
            }

        }

        public void requestWriteSettingsPermission() {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle("Auto-rotate and auto-brightness fix");
            builder.setMessage("ForceDoze requires permission to modify the auto-rotate and auto-brightness setting on your phone in order to use this feature. " +
                    "Press 'Authorize' to grant permission, or press 'Cancel' to deny");
            builder.setPositiveButton("Authorize", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                    startActivity(intent);
                }
            });
            builder.setNegativeButton("Deny", new DialogInterface.OnClickListener() {
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
            if (Utils.isDeviceRunningOnNPreview()) {
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
            Log.i(TAG, "ForceDoze reset procedure complete");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle("Reset complete");
            builder.setMessage("ForceDoze has resetted itself. The following changes were made: \n\n1) Stop " +
                    "ForceDoze service\n2) Re-enable sensors, just in case they are disabled\n3) Disable and " +
                    "re-enable Doze mode, to ensure Doze mode is turned on properly\n4) Reset app preferences\n5) " +
                    "Revoke DUMP and READ_LOGS (if granted) permission\n\nIt is recommended that you restart your device. The app will restart now!");
            builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
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

package com.suyashsrijan.forcedoze;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends AppCompatActivity {
    public static String TAG = "ForceDoze";

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
        Intent intent = new Intent("reload-settings");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public static class SettingsFragment extends PreferenceFragment{

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            Preference resetForceDozePref = (Preference) findPreference("resetForceDoze");
            Preference debugLogPref = (Preference)findPreference("debugLogs");
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

            autoRotateFixPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (!Settings.System.canWrite(getActivity())) {
                        requestWriteSettingsPermission();
                        return false;
                    } else return true;
                }
            });

            boolean isSuAvailable = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("isSuAvailable", false);
            if (!isSuAvailable) {
                debugLogPref.setSummary("Disabled because this feature requires root");
                debugLogPref.setEnabled(false);
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
            executeCommand("dumpsys deviceidle disable");
            executeCommand("dumpsys deviceidle enable");
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
            boolean isSuAvailable = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("isSuAvailable", false);
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

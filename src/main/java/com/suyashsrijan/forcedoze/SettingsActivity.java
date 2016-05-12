package com.suyashsrijan.forcedoze;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.IOException;

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

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);

            Preference button = (Preference)findPreference("resetForceDoze");
            button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                    builder.setTitle("Do you want to reset?");
                    builder.setMessage("Resetting ForceDoze will make the following changes: \n\n1) Stop " +
                            "ForceDoze service\n2) Re-enable sensors, just in case they are disabled\n3) Disable and " +
                            "re-enable Doze mode, to ensure Doze mode is turned on properly\n4) Reset app preferences\n5) " +
                            "Revoke DUMP permission\n\nDo you want to continue?");
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
            Log.i(TAG, "ForceDoze reset procedure complete");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle("Reset complete");
            builder.setMessage("ForceDoze has resetted itself. The following changes were made: \n\n1) Stop " +
                    "ForceDoze service\n2) Re-enable sensors, just in case they are disabled\n3) Disable and " +
                    "re-enable Doze mode, to ensure Doze mode is turned on properly\n4) Reset app preferences\n5) " +
                    "Revoke DUMP permission\n\nIt is recommended that you restart your device. The app will restart now!");
            builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    ProcessPhoenix.triggerRebirth(getActivity());
                }
            });
            builder.show();
        }

        public void executeCommand(String command) {
            Boolean isSuAvailable = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("isSuAvailable", false);
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
}

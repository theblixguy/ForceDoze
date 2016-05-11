package com.suyashsrijan.forcedoze;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import eu.chainfire.libsuperuser.Shell;

public class WhitelistApps extends AppCompatActivity {
    ListView listView;
    SharedPreferences sharedPreferences;
    WhitelistAppsAdapter whitelistAppsAdapter;
    Set<String> whitelistedPackages;
    public ArrayList<WhitelistAppsItem> listData = new ArrayList<>();
    public static String TAG = "ForceDoze";
    Boolean isSuAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whitelist_apps);
        listView = (ListView) findViewById(R.id.listView2);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        whitelistedPackages = sharedPreferences.getStringSet("whitelistApps", new HashSet<String>());
        Log.i(TAG, "Whitelisted packages: " + whitelistedPackages.size());
        if (!whitelistedPackages.isEmpty()) {
            for (String s : whitelistedPackages) {
                WhitelistAppsItem appItem = new WhitelistAppsItem();
                appItem.setAppPackageName(s);
                try {
                    appItem.setAppName(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(s, PackageManager.GET_META_DATA)).toString());
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                listData.add(appItem);
            }
        }
        whitelistAppsAdapter = new WhitelistAppsAdapter(this, listData);
        listView.setAdapter(whitelistAppsAdapter);
        isSuAvailable = sharedPreferences.getBoolean("isSuAvailable", false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.whitelist_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_add_whitelist:
                new MaterialDialog.Builder(this)
                        .title("Whitelist app")
                        .content("Please enter the package name of the app you want to whitelist from Doze")
                        .inputType(InputType.TYPE_CLASS_TEXT)
                        .input("com.spotify.music", "", false, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                if (whitelistedPackages.contains(input.toString())) {
                                    displayDialog("Info", "The app you're trying to add is already whitelisted");
                                } else {
                                    WhitelistAppsItem appItem = new WhitelistAppsItem();
                                    appItem.setAppPackageName(input.toString());
                                    whitelistedPackages.add(input.toString());
                                    try {
                                        appItem.setAppName(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(input.toString(), PackageManager.GET_META_DATA)).toString());
                                        listData.add(appItem);
                                        whitelistAppsAdapter.notifyDataSetChanged();
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.remove("whitelistApps");
                                        editor.apply();
                                        editor.putStringSet("whitelistApps", whitelistedPackages);
                                        editor.apply();
                                        modifyWhitelist(input.toString(), false);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }).show();
                break;
            case R.id.action_remove_whitelist:
                new MaterialDialog.Builder(this)
                        .title("Whitelist app")
                        .content("Please enter the package name of the app you want to remove from the whitelist")
                        .inputType(InputType.TYPE_CLASS_TEXT)
                        .input("com.spotify.music", "", false, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                if (!whitelistedPackages.contains(input.toString())) {
                                    displayDialog("Info", "The app you're trying to remove does not exist in the whitelist");
                                } else {
                                    WhitelistAppsItem appItem = new WhitelistAppsItem();
                                    appItem.setAppPackageName(input.toString());
                                    try {
                                        appItem.setAppName(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(input.toString(), PackageManager.GET_META_DATA)).toString());
                                        for (WhitelistAppsItem item : listData) {
                                            if (item.getAppPackageName().equals(input.toString())) {
                                                listData.remove(item);
                                            }
                                        }
                                        whitelistAppsAdapter.notifyDataSetChanged();
                                        whitelistedPackages.remove(input.toString());
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.remove("whitelistApps");
                                        editor.apply();
                                        editor.putStringSet("whitelistApps", whitelistedPackages);
                                        editor.apply();
                                        modifyWhitelist(input.toString(), true);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }).show();
        }
        return super.onOptionsItemSelected(item);
    }

    public void modifyWhitelist(String packageName, boolean remove){
        if (remove) {
            Log.i(TAG, "Removing app " + packageName + " from Doze whitelist");
            executeCommand("dumpsys deviceidle whitelist -" + packageName);
        } else {
            Log.i(TAG, "Adding app " + packageName + " to Doze whitelist");
            executeCommand("dumpsys deviceidle whitelist +" + packageName);
        }
    }

    public void displayDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("Close", null);
        builder.show();
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

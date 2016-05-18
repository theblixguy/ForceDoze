package com.suyashsrijan.forcedoze;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DozeBatteryConsumption extends AppCompatActivity {
    ArrayList<BatteryConsumptionItem> batteryConsumptionItems;
    Set<String> dozeUsageStats;
    ListView listView;
    BatteryConsumptionAdapter batteryConsumptionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doze_battery_consumption);
        listView = (ListView) findViewById(R.id.listView);
        batteryConsumptionItems = new ArrayList<>();
        dozeUsageStats = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("dozeUsageData", new LinkedHashSet<String>());
        if (dozeUsageStats.size() > 150) {
            if (Utils.isMyServiceRunning(ForceDozeService.class, this)) {
                stopService(new Intent(this, ForceDozeService.class));
            }
            clearStats();
            if (!Utils.isMyServiceRunning(ForceDozeService.class, this)) {
                startService(new Intent(this, ForceDozeService.class));
            }
        }
        if (!dozeUsageStats.isEmpty()) {
            ArrayList<String> sortedList = new ArrayList(dozeUsageStats);
            Collections.sort(sortedList);
            for (int i = 0; i < dozeUsageStats.size(); i++) {
                BatteryConsumptionItem item = new BatteryConsumptionItem();
                item.setTimestampPercCombo(sortedList.get(i));
                batteryConsumptionItems.add(item);
            }
        }
        batteryConsumptionAdapter = new BatteryConsumptionAdapter(this, batteryConsumptionItems);
        listView.setAdapter(batteryConsumptionAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.doze_stats_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_clear_stats:
                clearStats();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void clearStats() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("dozeUsageData");
        editor.apply();
        if (Utils.isMyServiceRunning(ForceDozeService.class, DozeBatteryConsumption.this)) {
            Intent intent = new Intent("reload-settings");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        batteryConsumptionItems.clear();
        batteryConsumptionAdapter.notifyDataSetChanged();
    }
}

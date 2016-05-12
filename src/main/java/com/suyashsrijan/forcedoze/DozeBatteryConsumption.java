package com.suyashsrijan.forcedoze;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;

import java.util.ArrayList;
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
        if (!dozeUsageStats.isEmpty()) {
            for (String s : dozeUsageStats) {
                BatteryConsumptionItem item = new BatteryConsumptionItem();
                item.setTimestampPercCombo(s);
                batteryConsumptionItems.add(item);
            }
        }
        batteryConsumptionAdapter = new BatteryConsumptionAdapter(this, batteryConsumptionItems);
        listView.setAdapter(batteryConsumptionAdapter);
    }
}

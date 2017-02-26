package com.suyashsrijan.forcedoze;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.ListView;

import java.util.ArrayList;

public class TaskerBroadcastsActivity extends AppCompatActivity {

    ArrayList<TaskerBroadcastsItem> items;
    ListView listView;
    TaskerBroadcastsAdapter taskerBroadcastsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_broadcasts);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = (ListView) findViewById(R.id.listViewBroadcasts);
        items = new ArrayList<>();
        items.add(new TaskerBroadcastsItem("com.suyashsrijan.forcedoze.ENABLE_FORCEDOZE",
                "Broadcast values required: None"));
        items.add(new TaskerBroadcastsItem("com.suyashsrijan.forcedoze.DISABLE_FORCEDOZE",
                "Broadcast values required: None"));
        items.add(new TaskerBroadcastsItem("com.suyashsrijan.forcedoze.ADD_WHITELIST",
                "Broadcast values required:\npackageName\n\npackageName has to be the full " +
                        "package name of the app you want to add to the whitelist"));
        items.add(new TaskerBroadcastsItem("com.suyashsrijan.forcedoze.REMOVE_WHITELIST",
                "Broadcast values required:\npackageName\n\npackageName has to be the full " +
                        "package name of the app you want to remove from the whitelist"));
        items.add(new TaskerBroadcastsItem("com.suyashsrijan.forcedoze.CHANGE_SETTING",
                "Broadcast values required:\nsettingName\nsettingValue\n\nsettingName can be one of the following:" +
                        "\n1) turnOffDataInDoze\n2) turnOffWiFiInDoze\n3) ignoreLockscreenTimeout" +
                        "\n4) dozeEnterDelay\n5) useAutoRotateAndBrightnessFix\n6) enableSensors" +
                        "\n7) disableWhenCharging\n8) showPersistentNotif\n9) useXposedSensorWorkaround" +
                        "\n10) useNonRootSensorWorkaround\n\nsettingValue can be one of the " +
                        "following:\n1) true\n2) false\n3) an integer value (ONLY in case of dozeEnterDelay)"));
        taskerBroadcastsAdapter = new TaskerBroadcastsAdapter(this, items);
        listView.setAdapter(taskerBroadcastsAdapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
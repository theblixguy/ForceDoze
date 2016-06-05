package com.suyashsrijan.forcedoze;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import de.psdev.licensesdialog.LicensesDialog;
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20;
import de.psdev.licensesdialog.licenses.MITLicense;
import de.psdev.licensesdialog.model.Notice;
import de.psdev.licensesdialog.model.Notices;

public class AboutAppActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_app);
        if (getSupportActionBar() != null)  {
            getSupportActionBar().setElevation(0f);
        }
    }

    public void showLicences(View v) {
        final Notices notices = new Notices();
        notices.addNotice(new Notice("Material Dialogs", "https://github.com/afollestad/material-dialogs", "Aidan Michael Follestad", new MITLicense()));
        notices.addNotice(new Notice("libsuperuser", "https://github.com/Chainfire/libsuperuser", "Chainfire", new ApacheSoftwareLicense20()));
        notices.addNotice(new Notice("Nanotasks", "https://github.com/fabiendevos/nanotasks", "Fabien Devos", new ApacheSoftwareLicense20()));
        notices.addNotice(new Notice("ProcessPhoenix", "https://github.com/JakeWharton/ProcessPhoenix", "Jake Wharton", new ApacheSoftwareLicense20()));
        notices.addNotice(new Notice("ckChangelog", "https://github.com/cketti/ckChangeLog", "cketti", new ApacheSoftwareLicense20()));
        notices.addNotice(new Notice("SimpleCustomTabs", "https://github.com/eliseomartelli/SimpleCustomTabs", "Eliseo Martelli", new MITLicense()));
        new LicensesDialog.Builder(this).setNotices(notices).setIncludeOwnLicense(true).build().show();
    }
}

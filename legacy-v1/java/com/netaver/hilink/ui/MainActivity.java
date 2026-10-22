package com.netaver.hilink.ui;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.widget.Button;

import com.netaver.hilink.HiLinkApp;
import com.netaver.hilink.R;

/** Main activity: bottom nav switching fragments. */
public class MainActivity extends Activity {
    private Button[] navButtons;
    private int currentTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        navButtons = new Button[]{
                findViewById(R.id.nav_dashboard), findViewById(R.id.nav_band),
                findViewById(R.id.nav_sms), findViewById(R.id.nav_settings)
        };
        for (int i = 0; i < navButtons.length; i++) {
            final int idx = i;
            navButtons[i].setOnClickListener(v -> switchTab(idx));
        }
        switchTab(0);
    }

    private void switchTab(int idx) {
        if (idx == currentTab) return;
        currentTab = idx;
        for (int i = 0; i < navButtons.length; i++) {
            navButtons[i].setTextColor(i == idx ? 0xFF60A5FA : 0xFF8B93A7);
        }
        Fragment f;
        switch (idx) {
            case 1: f = new BandFragment(); break;
            case 2: f = new SmsFragment(); break;
            case 3: f = new SettingsFragment(); break;
            default: f = new DashboardFragment(); break;
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f).commit();
    }

    public HiLinkApp app() { return (HiLinkApp) getApplication(); }
}

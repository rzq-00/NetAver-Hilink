package com.netaver.hilink;

import android.app.Application;

import com.netaver.hilink.hilink.Hilink;
import com.netaver.hilink.util.Prefs;

/** Application singleton: prefs + shared HiLink client. */
public class HiLinkApp extends Application {
    private Prefs prefs;
    private Hilink hilink;
    private String hilinkKey = "";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
    }

    public Prefs prefs() { return prefs; }

    /** Shared client, rebuilt when ip/user/pass change. Thread-safe. */
    public synchronized Hilink hilink() {
        String key = prefs.routerIp() + "|" + prefs.username() + "|" + prefs.password();
        if (hilink == null || !key.equals(hilinkKey)) {
            hilink = new Hilink(prefs.routerIp(), prefs.username(), prefs.password());
            hilinkKey = key;
        }
        return hilink;
    }
}

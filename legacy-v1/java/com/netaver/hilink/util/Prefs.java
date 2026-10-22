package com.netaver.hilink.util;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings: router connection. */
public class Prefs {
    private static final String NAME = "netaver_hilink";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String routerIp() { return sp.getString("router_ip", "192.168.8.1"); }
    public void setRouterIp(String v) { sp.edit().putString("router_ip", v).apply(); }

    public String username() { return sp.getString("username", "admin"); }
    public void setUsername(String v) { sp.edit().putString("username", v).apply(); }

    public String password() { return sp.getString("password", ""); }
    public void setPassword(String v) { sp.edit().putString("password", v).apply(); }

    public int refreshSecs() { return sp.getInt("refresh_secs", 5); }
    public void setRefreshSecs(int v) { sp.edit().putInt("refresh_secs", v).apply(); }
}

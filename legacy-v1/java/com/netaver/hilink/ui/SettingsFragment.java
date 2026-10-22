package com.netaver.hilink.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.netaver.hilink.HiLinkApp;
import com.netaver.hilink.R;
import com.netaver.hilink.hilink.Hilink;
import com.netaver.hilink.util.Prefs;

/** Settings: router IP, credentials, test login, reboot. */
public class SettingsFragment extends Fragment {
    private EditText ipEt, userEt, passEt;
    private TextView statusTv;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        final Prefs p = app.prefs();
        ipEt = v.findViewById(R.id.set_ip);
        userEt = v.findViewById(R.id.set_user);
        passEt = v.findViewById(R.id.set_pass);
        statusTv = v.findViewById(R.id.set_status);
        ipEt.setText(p.routerIp());
        userEt.setText(p.username());
        passEt.setText(p.password());

        v.findViewById(R.id.set_save).setOnClickListener(x -> {
            p.setRouterIp(ipEt.getText().toString().trim());
            p.setUsername(userEt.getText().toString().trim());
            p.setPassword(passEt.getText().toString());
            statusTv.setText("Tersimpan");
            Toast.makeText(getActivity(), "Tersimpan", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.set_login).setOnClickListener(x -> testLogin());
        v.findViewById(R.id.set_reboot).setOnClickListener(x ->
                new AlertDialog.Builder(getActivity())
                        .setMessage("Reboot router sekarang?")
                        .setPositiveButton("Reboot", (d, w) -> reboot())
                        .setNegativeButton("Batal", null)
                        .show());
        return v;
    }

    private void testLogin() {
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        final Prefs p = app.prefs();
        p.setRouterIp(ipEt.getText().toString().trim());
        p.setUsername(userEt.getText().toString().trim());
        p.setPassword(passEt.getText().toString());
        statusTv.setText("Menghubungkan...");
        new Thread(() -> {
            try {
                app.hilink().login();
                ui("Login berhasil ✓");
            } catch (Exception e) {
                ui("Gagal: " + (e.getMessage() == null ? "koneksi gagal" : e.getMessage()));
            }
        }).start();
    }

    private void reboot() {
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        new Thread(() -> {
            try {
                app.hilink().reboot();
                ui("Perintah reboot terkirim");
            } catch (Exception e) {
                ui("Gagal: " + (e.getMessage() == null ? "koneksi gagal" : e.getMessage()));
            }
        }).start();
    }

    private void ui(final String s) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (getActivity() == null) return;
            statusTv.setText(s);
            Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show();
        });
    }
}

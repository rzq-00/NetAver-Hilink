package com.netaver.hilink.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.netaver.hilink.HiLinkApp;
import com.netaver.hilink.R;
import com.netaver.hilink.hilink.Hilink;

import java.util.ArrayList;
import java.util.List;

/** SMS inbox: list, read, send, delete. */
public class SmsFragment extends Fragment {
    private ListView list;
    private TextView errorTv;
    private EditText phoneEt, textEt;
    private final List<Hilink.Sms> items = new ArrayList<>();
    private SmsAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_sms, container, false);
        list = v.findViewById(R.id.sms_list);
        errorTv = v.findViewById(R.id.sms_error);
        phoneEt = v.findViewById(R.id.sms_phone);
        textEt = v.findViewById(R.id.sms_text);
        adapter = new SmsAdapter();
        list.setAdapter(adapter);
        v.findViewById(R.id.sms_refresh).setOnClickListener(x -> load());
        v.findViewById(R.id.sms_send).setOnClickListener(x -> send());
        load();
        return v;
    }

    private HiLinkApp app() { return (HiLinkApp) getActivity().getApplication(); }

    private void load() {
        if (getActivity() == null) return;
        errorTv.setText("Memuat...");
        new Thread(() -> {
            try {
                final List<Hilink.Sms> sms = app().hilink().getSms(1, 20);
                ui(() -> {
                    errorTv.setText(sms.isEmpty() ? "Inbox kosong" : "");
                    items.clear();
                    items.addAll(sms);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                ui(() -> errorTv.setText("Gagal: " + msg(e)));
            }
        }).start();
    }

    private void send() {
        final String phone = phoneEt.getText().toString().trim();
        final String text = textEt.getText().toString().trim();
        if (phone.isEmpty() || text.isEmpty()) {
            Toast.makeText(getActivity(), "Isi nomor dan pesan", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                app().hilink().sendSms(phone, text);
                ui(() -> {
                    Toast.makeText(getActivity(), "SMS terkirim", Toast.LENGTH_SHORT).show();
                    textEt.setText("");
                });
            } catch (Exception e) {
                ui(() -> Toast.makeText(getActivity(), "Gagal: " + msg(e),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void delete(final Hilink.Sms s) {
        new AlertDialog.Builder(getActivity())
                .setMessage("Hapus SMS dari " + s.phone + "?")
                .setPositiveButton("Hapus", (d, w) -> new Thread(() -> {
                    try {
                        app().hilink().deleteSms(s.index);
                        ui(() -> { load(); });
                    } catch (Exception e) {
                        ui(() -> Toast.makeText(getActivity(), "Gagal: " + msg(e),
                                Toast.LENGTH_LONG).show());
                    }
                }).start())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void ui(Runnable r) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> { if (getActivity() != null) r.run(); });
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? "koneksi gagal" : e.getMessage();
    }

    private class SmsAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(getActivity())
                    .inflate(R.layout.item_sms, parent, false);
            Hilink.Sms s = items.get(pos);
            TextView from = cv.findViewById(R.id.sms_from);
            from.setText(("0".equals(s.read) ? "● " : "") + s.phone);
            ((TextView) cv.findViewById(R.id.sms_date)).setText(s.date);
            ((TextView) cv.findViewById(R.id.sms_body)).setText(s.content);
            cv.findViewById(R.id.sms_delete).setOnClickListener(x -> delete(s));
            cv.setOnClickListener(x -> {
                // mark read in background; clear the dot locally
                if ("0".equals(s.read)) {
                    s.read = "1";
                    notifyDataSetChanged();
                    new Thread(() -> {
                        try { app().hilink().markSmsRead(s.index); }
                        catch (Exception ignored) {}
                    }).start();
                }
            });
            return cv;
        }
    }
}

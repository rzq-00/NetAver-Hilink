package com.netaver.hilink.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.netaver.hilink.HiLinkApp;
import com.netaver.hilink.R;
import com.netaver.hilink.hilink.Hilink;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** LTE band lock + network mode. Bands as hex bitmask (bit n-1 = band n). */
public class BandFragment extends Fragment {
    // {name, freq, hexbit}
    private static final String[][] BANDS = {
            {"B1", "2100 MHz", "1"},
            {"B3", "1800 MHz", "4"},
            {"B5", "850 MHz", "10"},
            {"B7", "2600 MHz", "40"},
            {"B8", "900 MHz", "80"},
            {"B20", "800 MHz", "80000"},
            {"B28", "700 MHz", "8000000"},
            {"B38", "2600 MHz TDD", "2000000000"},
            {"B40", "2300 MHz", "8000000000"},
            {"B41", "2500 MHz", "10000000000"},
    };
    private static final String ALL_BANDS = "7FFFFFFFFFFFFFFF";

    private final List<Switch> bandSwitches = new ArrayList<>();
    private RadioGroup modeGroup;
    private TextView statusTv;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_band, container, false);
        modeGroup = v.findViewById(R.id.mode_group);
        statusTv = v.findViewById(R.id.band_status);
        LinearLayout list = v.findViewById(R.id.band_list);
        for (String[] b : BANDS) {
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(4, 10, 4, 10);
            TextView tv = new TextView(getActivity());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setText(b[0] + " · " + b[1]);
            tv.setTextColor(0xFFF1F5F9);
            tv.setTextSize(14);
            Switch sw = new Switch(getActivity());
            sw.setTag(b[2]);
            row.addView(tv);
            row.addView(sw);
            list.addView(row);
            bandSwitches.add(sw);
        }
        v.findViewById(R.id.band_apply).setOnClickListener(x -> apply());
        loadCurrent();
        return v;
    }

    private void loadCurrent() {
        if (getActivity() == null) return;
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        statusTv.setText("Memuat...");
        new Thread(() -> {
            try {
                Hilink.NetMode n = app.hilink().getNetMode();
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getActivity() == null) return;
                    statusTv.setText("");
                    int id = "03".equals(n.networkMode) ? R.id.mode_lte
                            : "02".equals(n.networkMode) ? R.id.mode_3g : R.id.mode_auto;
                    modeGroup.check(id);
                    BigInteger cur = parseHex(n.lteBand);
                    boolean all = cur.toString(16).equalsIgnoreCase(ALL_BANDS)
                            || n.lteBand.isEmpty();
                    for (Switch sw : bandSwitches) {
                        BigInteger bit = new BigInteger((String) sw.getTag(), 16);
                        sw.setChecked(all || cur.and(bit).equals(bit));
                    }
                });
            } catch (Exception e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getActivity() == null) return;
                    statusTv.setText("Gagal memuat: " + msg(e));
                });
            }
        }).start();
    }

    private void apply() {
        int checked = modeGroup.getCheckedRadioButtonId();
        final String mode = checked == R.id.mode_lte ? "03"
                : checked == R.id.mode_3g ? "02" : "00";
        BigInteger mask = BigInteger.ZERO;
        for (Switch sw : bandSwitches) {
            if (sw.isChecked()) mask = mask.or(new BigInteger((String) sw.getTag(), 16));
        }
        if (mask.equals(BigInteger.ZERO)) {
            Toast.makeText(getActivity(), "Pilih minimal 1 band", Toast.LENGTH_SHORT).show();
            return;
        }
        final String hex = mask.toString(16).toUpperCase();
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        statusTv.setText("Menerapkan...");
        new Thread(() -> {
            try {
                app.hilink().setNetMode(mode, hex);
                ui("Berhasil. Router akan reconnect.");
            } catch (Exception e) {
                ui("Gagal: " + msg(e));
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

    private static BigInteger parseHex(String h) {
        try {
            h = h.trim();
            if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
            return new BigInteger(h, 16);
        } catch (Exception e) { return BigInteger.ZERO; }
    }

    private static String msg(Exception e) {
        if (e instanceof Hilink.HilinkException) return e.getMessage();
        return e.getMessage() == null ? "koneksi gagal" : e.getMessage();
    }
}

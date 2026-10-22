package com.netaver.hilink.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.netaver.hilink.HiLinkApp;
import com.netaver.hilink.R;
import com.netaver.hilink.hilink.Hilink;

/** Dashboard: status, signal, data usage, device info. Auto-refreshes. */
public class DashboardFragment extends Fragment {
    private TextView status, nettype, error;
    private TextView rssi, rsrp, rsrq, sinr, cell;
    private TextView dataToday, dataMonth, dataRate, devInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private volatile boolean loading;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        status = v.findViewById(R.id.dash_status);
        nettype = v.findViewById(R.id.dash_nettype);
        error = v.findViewById(R.id.dash_error);
        rssi = v.findViewById(R.id.sig_rssi);
        rsrp = v.findViewById(R.id.sig_rsrp);
        rsrq = v.findViewById(R.id.sig_rsrq);
        sinr = v.findViewById(R.id.sig_sinr);
        cell = v.findViewById(R.id.sig_cell);
        dataToday = v.findViewById(R.id.data_today);
        dataMonth = v.findViewById(R.id.data_month);
        dataRate = v.findViewById(R.id.data_rate);
        devInfo = v.findViewById(R.id.dev_info);
        v.findViewById(R.id.dash_refresh).setOnClickListener(x -> load());
        ticker = new Runnable() {
            @Override public void run() {
                load();
                handler.postDelayed(this, 10000);
            }
        };
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
        handler.postDelayed(ticker, 10000);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void load() {
        if (loading || getActivity() == null) return;
        loading = true;
        final HiLinkApp app = (HiLinkApp) getActivity().getApplication();
        new Thread(() -> {
            String err = "";
            Hilink.Status st = null;
            Hilink.Signal sg = null;
            Hilink.Traffic tr = null;
            Hilink.MonthStat mo = null;
            Hilink.DeviceInfo di = null;
            try {
                Hilink h = app.hilink();
                st = h.getStatus();
                sg = h.getSignal();
                tr = h.getTraffic();
                try { mo = h.getMonthStat(); } catch (Exception ignored) {}
                try { di = h.getDeviceInfo(); } catch (Exception ignored) {}
            } catch (Exception e) {
                err = e.getMessage() == null ? "Gagal konek ke router" : e.getMessage();
            }
            final String ferr = err;
            final Hilink.Status fst = st;
            final Hilink.Signal fsg = sg;
            final Hilink.Traffic ftr = tr;
            final Hilink.MonthStat fmo = mo;
            final Hilink.DeviceInfo fdi = di;
            if (getActivity() == null) { loading = false; return; }
            getActivity().runOnUiThread(() -> {
                loading = false;
                if (getActivity() == null) return;
                if (!ferr.isEmpty()) {
                    error.setText(ferr);
                    status.setText("Tidak terhubung");
                    return;
                }
                error.setText("");
                boolean conn = "901".equals(fst.connectionStatus);
                status.setText(conn ? "Terhubung" : "Tidak ada layanan");
                status.setTextColor(conn ? 0xFF4ADE80 : 0xFFF87171);
                nettype.setText(netName(fst.networkType));
                rssi.setText(fmtDb(fsg.rssi, "dBm"));
                rsrp.setText(fmtDb(fsg.rsrp, "dBm"));
                rsrq.setText(fmtDb(fsg.rsrq, "dB"));
                sinr.setText(fmtDb(fsg.sinr, "dB"));
                cell.setText(fsg.cellId.isEmpty() ? "" : "Cell ID: " + fsg.cellId);
                dataToday.setText("Hari ini: " + fmtBytes(ftr.curDown + ftr.curUp));
                dataMonth.setText("Bulan ini: " + (fmo != null ? fmtBytes(fmo.monthDown + fmo.monthUp) : "—"));
                dataRate.setText("↓ " + fmtRate(ftr.curDownRate) + "   ↑ " + fmtRate(ftr.curUpRate));
                if (fdi != null && !fdi.deviceName.isEmpty()) {
                    devInfo.setText(fdi.deviceName
                            + (fdi.swVer.isEmpty() ? "" : "\nSW: " + fdi.swVer)
                            + (fdi.imei.isEmpty() ? "" : "\nIMEI: " + fdi.imei));
                }
            });
        }).start();
    }

    private static String fmtDb(String raw, String unit) {
        if (raw == null || raw.isEmpty()) return "—";
        return raw + " " + unit;
    }

    private static String netName(String t) {
        if (t == null) return "";
        switch (t) {
            case "101": case "19": return "4G";
            case "4": case "5": case "6": case "7": case "9": case "17": return "3G";
            case "1": case "2": case "3": return "2G";
            case "0": return "";
            default: return t;
        }
    }

    private static String fmtBytes(long b) {
        if (b >= 1_000_000_000L) return String.format("%.1f GB", b / 1_000_000_000.0);
        if (b >= 1_000_000L) return String.format("%.1f MB", b / 1_000_000.0);
        if (b >= 1_000L) return String.format("%.0f KB", b / 1_000.0);
        return b + " B";
    }

    private static String fmtRate(long bps) {
        if (bps >= 1_000_000L) return String.format("%.1f Mbps", bps / 1_000_000.0);
        if (bps >= 1_000L) return String.format("%.0f Kbps", bps / 1_000.0);
        return bps + " bps";
    }
}

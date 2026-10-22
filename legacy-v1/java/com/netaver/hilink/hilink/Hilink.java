package com.netaver.hilink.hilink;

import android.util.Base64;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Huawei HiLink router API client (original implementation).
 * Session flow: GET SesTokInfo -> POST /api/user/login (SHA256 password)
 * -> requests carry SessionID cookie + __RequestVerificationToken header.
 * All XML parsing is defensive: missing tags yield "".
 */
public class Hilink {
    private static final int CONN_MS = 8000;
    private static final int READ_MS = 12000;

    public static class Status {
        public String connectionStatus = ""; // 901 = connected
        public String signalIcon = "";       // 0..5
        public String networkType = "";      // e.g. 101 = LTE
        public String serviceDomain = "";
        public String roaming = "";
        public String simStatus = "";        // from /api/monitoring/status? no -> separate
    }

    public static class Signal {
        public String rssi = "", rsrp = "", rsrq = "", sinr = "";
        public String rscp = "", ecio = "", cellId = "";
        public String ulBw = "", dlBw = "";
    }

    public static class Traffic {
        public long curUp, curDown, totalUp, totalDown;
        public long curUpRate, curDownRate;
        public String connectTime = "";
    }

    public static class MonthStat {
        public long monthDown, monthUp;
        public String duration = "";
    }

    public static class DeviceInfo {
        public String deviceName = "", serial = "", imei = "";
        public String hwVer = "", swVer = "", mac = "";
    }

    public static class NetMode {
        public String networkMode = ""; // 00 auto, 03 LTE only, 02 3G only
        public String networkBand = "";
        public String lteBand = "";     // hex bitmask
    }

    public static class Sms {
        public String index = "", phone = "", content = "", date = "";
        public String read = ""; // Smstat: 0 unread, 1 read
    }

    public static class HilinkException extends Exception {
        public final String code;
        HilinkException(String code, String msg) { super(msg); this.code = code; }
    }

    private final String base;
    private final String username;
    private final String password;
    private String sessionId = "";
    private String token = "";

    public Hilink(String ip, String username, String password) {
        this.base = "http://" + ip;
        this.username = username == null || username.isEmpty() ? "admin" : username;
        this.password = password == null ? "" : password;
    }

    // ---------------- session ----------------

    private synchronized void fetchSesTok() throws Exception {
        Map<String, String> m = get("/api/webserver/SesTokInfo");
        String ses = m.get("SesInfo");
        String tok = m.get("TokInfo");
        if (ses != null && ses.startsWith("SessionID=")) sessionId = ses.substring(10);
        else if (ses != null && !ses.isEmpty()) sessionId = ses;
        if (tok != null) token = tok;
    }

    /** Login (idempotent). Throws HilinkException on bad credentials. */
    public synchronized void login() throws Exception {
        fetchSesTok();
        String pwHash = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(password.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<request><Username>" + esc(username) + "</Username>"
                + "<Password>" + pwHash + "</Password>"
                + "<password_type>4</password_type></request>";
        Map<String, String> r = postRaw("/api/user/login", body);
        String code = r.get("__code");
        if (code != null) {
            if ("108003".equals(code)) { /* already logged in */ }
            else throw new HilinkException(code, loginError(code));
        }
        fetchSesTok(); // fresh token after login
    }

    private static String loginError(String code) {
        switch (code) {
            case "108006": return "Password salah";
            case "108007": return "Terlalu banyak percobaan, coba lagi nanti";
            default: return "Login gagal (" + code + ")";
        }
    }

    // ---------------- low level ----------------

    private Map<String, String> get(String path) throws Exception {
        return request("GET", path, null, false);
    }

    /** GET with login-on-demand (for endpoints that need auth on some firmware). */
    private Map<String, String> getAuth(String path) throws Exception {
        return request("GET", path, null, true);
    }

    private Map<String, String> post(String path, String body) throws Exception {
        return request("POST", path, body, true);
    }

    /** POST without login dance (used by login itself). */
    private Map<String, String> postRaw(String path, String body) throws Exception {
        return request("POST", path, body, false);
    }

    private synchronized Map<String, String> request(String method, String path,
                                                     String body, boolean needAuth) throws Exception {
        // lazy login: public endpoints work without it on most firmware
        Map<String, String> r = doRequest(method, path, body);
        String code = r.get("__code");
        if (needAuth && ("125002".equals(code) || "108002".equals(code) || "125003".equals(code))) {
            login();
            r = doRequest(method, path, body);
            code = r.get("__code");
            if ("125002".equals(code) || "108002".equals(code) || "125003".equals(code)) {
                throw new HilinkException(code, "Butuh login");
            }
        }
        if (code != null) throw new HilinkException(code, "Router error " + code);
        return r;
    }

    private Map<String, String> doRequest(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(CONN_MS);
        c.setReadTimeout(READ_MS);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "*/*");
        if (!sessionId.isEmpty()) c.setRequestProperty("Cookie", "SessionID=" + sessionId);
        if (!token.isEmpty()) c.setRequestProperty("__RequestVerificationToken", token);
        if (body != null) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/xml");
            c.setRequestProperty("Content-Length", Integer.toString(b.length));
            try (OutputStream o = c.getOutputStream()) { o.write(b); }
        }
        int rc = c.getResponseCode();
        InputStream in = rc >= 400 ? c.getErrorStream() : c.getInputStream();
        String xml = in == null ? "" : readAll(in);
        c.disconnect();
        return parseXml(xml);
    }

    // ---------------- public API ----------------

    public Status getStatus() throws Exception {
        Map<String, String> m = get("/api/monitoring/status");
        Status s = new Status();
        s.connectionStatus = v(m, "ConnectionStatus");
        s.signalIcon = v(m, "SignalIcon");
        s.networkType = v(m, "CurrentNetworkType");
        s.serviceDomain = v(m, "CurrentServiceDomain");
        s.roaming = v(m, "RoamingStatus");
        return s;
    }

    public Signal getSignal() throws Exception {
        Map<String, String> m = get("/api/device/signal");
        Signal s = new Signal();
        s.rssi = v(m, "rssi");
        s.rsrp = v(m, "rsrp");
        s.rsrq = v(m, "rsrq");
        s.sinr = v(m, "sinr");
        s.rscp = v(m, "rscp");
        s.ecio = v(m, "ecio");
        s.cellId = v(m, "cell_id");
        s.ulBw = v(m, "ulbandwidth");
        s.dlBw = v(m, "dlbandwidth");
        return s;
    }

    public DeviceInfo getDeviceInfo() throws Exception {
        Map<String, String> m = getAuth("/api/device/information");
        if (m.size() <= 1) m = post("/api/device/information", null); // __raw only = empty
        DeviceInfo d = new DeviceInfo();
        d.deviceName = v(m, "DeviceName");
        d.serial = v(m, "SerialNumber");
        d.imei = v(m, "Imei");
        d.hwVer = v(m, "HardwareVersion");
        d.swVer = v(m, "SoftwareVersion");
        d.mac = v(m, "MacAddress1");
        if (d.mac.isEmpty()) d.mac = v(m, "MacAddressWiFi");
        return d;
    }

    public Traffic getTraffic() throws Exception {
        Map<String, String> m = get("/api/monitoring/traffic-statistics");
        Traffic t = new Traffic();
        t.curUp = num(m, "CurrentUpload");
        t.curDown = num(m, "CurrentDownload");
        t.totalUp = num(m, "TotalUpload");
        t.totalDown = num(m, "TotalDownload");
        t.curUpRate = num(m, "CurrentUploadRate");
        t.curDownRate = num(m, "CurrentDownloadRate");
        t.connectTime = v(m, "CurrentConnectTime");
        return t;
    }

    public MonthStat getMonthStat() throws Exception {
        Map<String, String> m = get("/api/monitoring/month_statistics");
        MonthStat s = new MonthStat();
        s.monthDown = num(m, "CurrentMonthDownload");
        s.monthUp = num(m, "CurrentMonthUpload");
        s.duration = v(m, "MonthDuration");
        return s;
    }

    public NetMode getNetMode() throws Exception {
        Map<String, String> m = getAuth("/api/net/net-mode");
        if (m.size() <= 1) m = post("/api/net/net-mode", null);
        NetMode n = new NetMode();
        n.networkMode = v(m, "NetworkMode");
        n.networkBand = v(m, "NetworkBand");
        n.lteBand = v(m, "LTEBand");
        return n;
    }

    /** Set network mode + LTE band bitmask (hex, e.g. "4" = B3). */
    public void setNetMode(String mode, String lteBandHex) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>"
                + "<NetworkMode>" + mode + "</NetworkMode>"
                + "<NetworkBand>3FFFFFFF</NetworkBand>"
                + "<LTEBand>" + lteBandHex.toUpperCase() + "</LTEBand>"
                + "</request>";
        post("/api/net/net-mode", body);
    }

    public List<Sms> getSms(int page, int count) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>"
                + "<PageIndex>" + page + "</PageIndex>"
                + "<ReadCount>" + count + "</ReadCount>"
                + "<BoxType>1</BoxType><SortType>0</SortType>"
                + "<Ascending>0</Ascending><UnreadPreferred>0</UnreadPreferred>"
                + "</request>";
        Map<String, String> m = post("/api/sms/sms-list", body);
        List<Sms> out = new ArrayList<>();
        String raw = m.get("__raw");
        if (raw == null) return out;
        // extract each <Message>...</Message> block
        int i = 0;
        while ((i = raw.indexOf("<Message>", i)) >= 0) {
            int j = raw.indexOf("</Message>", i);
            if (j < 0) break;
            Map<String, String> mm = parseXml(raw.substring(i, j + 10));
            Sms s = new Sms();
            s.index = v(mm, "Index");
            s.phone = v(mm, "Phone");
            s.content = v(mm, "Content");
            s.date = v(mm, "Date");
            s.read = v(mm, "Smstat");
            out.add(s);
            i = j + 10;
        }
        return out;
    }

    public void sendSms(String phone, String content) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>"
                + "<Index>-1</Index><Phones><Phone>" + esc(phone) + "</Phone></Phones>"
                + "<Sca></Sca><Content>" + esc(content) + "</Content>"
                + "<Length>" + content.length() + "</Length>"
                + "<Reserved>1</Reserved><Date>-1</Date></request>";
        post("/api/sms/send-sms", body);
    }

    public void deleteSms(String index) throws Exception {
        post("/api/sms/delete-sms",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Index>"
                        + esc(index) + "</Index></request>");
    }

    public void markSmsRead(String index) throws Exception {
        try {
            post("/api/sms/set-read",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Index>"
                            + esc(index) + "</Index></request>");
        } catch (Exception ignored) { /* not supported everywhere */ }
    }

    /** Reboot the router. */
    public void reboot() throws Exception {
        post("/api/device/control",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Control>1</Control></request>");
    }

    // ---------------- helpers ----------------

    private static String v(Map<String, String> m, String k) {
        String s = m.get(k);
        return s == null ? "" : s;
    }

    private static long num(Map<String, String> m, String k) {
        try { return Long.parseLong(v(m, k)); } catch (Exception e) { return 0; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] t = new byte[8192];
        int n;
        while ((n = in.read(t)) > 0) b.write(t, 0, n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Flat XML -> map. Records __code on <error><code>, keeps __raw.
     * Nested <Message> blocks are NOT flattened (handled by caller).
     */
    private static Map<String, String> parseXml(String xml) {
        Map<String, String> m = new HashMap<>();
        if (xml == null || xml.isEmpty()) return m;
        m.put("__raw", xml);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new java.io.StringReader(xml));
            String cur = null;
            StringBuilder txt = new StringBuilder();
            int depth = 0;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    depth++;
                    if ("code".equals(p.getName()) && depth == 2) {
                        cur = "__code"; // <error><code>
                        txt.setLength(0);
                    } else if (depth == 2 && !"Message".equals(p.getName())
                            && !"Messages".equals(p.getName())) {
                        cur = p.getName();
                        txt.setLength(0);
                    } else {
                        cur = null;
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (cur != null) txt.append(p.getText());
                } else if (ev == XmlPullParser.END_TAG) {
                    if (cur != null && depth <= 3) m.put(cur, txt.toString().trim());
                    if (depth == 2) cur = null;
                    depth--;
                }
                ev = p.next();
            }
        } catch (Exception ignored) {}
        return m;
    }
}

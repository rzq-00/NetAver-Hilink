package com.netaver.hilink.net

import android.util.Base64
import android.util.Xml
import com.netaver.hilink.core.Errors
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * HiLink API client v2 — ONE persistent session for the whole app.
 *
 * Design rules (learned from the eyade autopsy):
 * - login happens once; every screen shares it; re-login is silent,
 *   single, and only on a real session error — never per request.
 * - a router lockout (108007) is respected: no login attempts are made
 *   until the lockout window has passed.
 * - the password hash scheme follows the router's own state-login
 *   (password_type 4 = token scheme, 1 = plain scheme); the scheme that
 *   worked is remembered by the caller via onSchemeLearned.
 * - token sources are tried in order: SesTokInfo, then webserver/token
 *   (some modded WebUIs answer only one of them).
 */
class HilinkClient(
    private val host: String,
    private val username: String,
    private val password: String,
    initialScheme: Int = -1
) {
    class HilinkException(val code: String, msg: String) : Exception(msg)

    data class Status(
        val connectionStatus: String = "", val signalIcon: String = "",
        val networkType: String = "", val roaming: String = "", val simStatus: String = "",
        val batteryLevel: String = "", val batteryStatus: String = ""
    )
    data class Signal(
        val rssi: String = "", val rsrp: String = "", val rsrq: String = "",
        val sinr: String = "", val rscp: String = "", val ecio: String = "",
        val cellId: String = "", val pci: String = "", val band: String = ""
    )
    data class Traffic(
        val curUp: Long = 0, val curDown: Long = 0, val totalUp: Long = 0, val totalDown: Long = 0,
        val upRate: Long = 0, val downRate: Long = 0, val connectTimeSec: Long = 0
    )
    data class MonthStat(val down: Long = 0, val up: Long = 0)
    data class DeviceInfo(
        val name: String = "", val serial: String = "", val imei: String = "",
        val hw: String = "", val sw: String = "", val mac: String = ""
    )
    data class NetMode(val mode: String = "", val networkBand: String = "", val lteBand: String = "")
    data class Host(val name: String = "", val ip: String = "", val mac: String = "", val active: String = "")
    data class Sms(val index: String = "", val phone: String = "", val content: String = "", val date: String = "", val read: String = "")
    data class Reach(val ok: Boolean, val loginState: String, val passwordType: Int, val note: String)

    private val base = "http://$host"
    private var sessionId = ""
    private var token = ""
    @Volatile var loggedIn = false; private set
    @Volatile var loginAtMs = 0L; private set
    private var lockoutUntilMs = 0L
    private val cookieJar = LinkedHashMap<String, String>()
    private var lastFailMs = 0L
    private var failStreak = 0
    private var lastFailMsg = ""

    var scheme = initialScheme; private set
    var onSchemeLearned: ((Int) -> Unit)? = null

    // ---------------- session ----------------

    @Synchronized
    fun ensureSession() {
        if (loggedIn && token.isNotEmpty()) return
        loginLocked(manual = false)
    }

    /** manual=true when the user explicitly tapped LOGIN (bypasses the fail backoff, never the router lockout). */
    @Synchronized
    fun login(manual: Boolean = false) = loginLocked(manual)

    private fun loginLocked(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (now < lockoutUntilMs) {
            val s = (lockoutUntilMs - now + 999) / 1000
            throw HilinkException("108007", "Router masih memblokir login (${s} dtk lagi). " + Errors.describe("108007"))
        }
        if (!manual && failStreak > 0) {
            val waitMs = minOf(120_000L, 20_000L shl minOf(failStreak - 1, 3))
            val elapsed = now - lastFailMs
            if (elapsed < waitMs) {
                val s = (waitMs - elapsed + 999) / 1000
                throw HilinkException("WAIT", "Login terakhir gagal. App menunggu jeda aman (${s} dtk) sebelum mencoba lagi agar router tidak memblokir. Terakhir: $lastFailMsg")
            }
        }
        try {
            loginSequence()
            failStreak = 0
            lastFailMs = 0
            lastFailMsg = ""
        } catch (e: HilinkException) {
            if (e.code != "108007") {
                failStreak++
                lastFailMs = System.currentTimeMillis()
                lastFailMsg = e.message ?: e.code
            }
            throw e
        }
    }

    private fun loginSequence() {
        val now = System.currentTimeMillis()
        fetchSesTok()
        val st = try { raw("GET", "/api/user/state-login", null) } catch (e: Exception) { emptyMap() }
        @Suppress("UNUSED_VARIABLE") val startedAt = now
        val pwType = st["password_type"]?.toIntOrNull() ?: 4
        val order = mutableListOf<Int>()
        if (scheme in 1..4) order.add(scheme)
        order.add(if (pwType == 1) 1 else 4)
        if (!order.contains(1)) order.add(1)
        if (!order.contains(4)) order.add(4)

        var lastErr: HilinkException? = null
        for (sch in order) {
            try {
                attemptLogin(sch)
                scheme = sch
                onSchemeLearned?.invoke(sch)
                loggedIn = true
                loginAtMs = System.currentTimeMillis()
                fetchSesTok() // fresh token after login
                return
            } catch (e: HilinkException) {
                lastErr = e
                when (e.code) {
                    "108003" -> { // already logged in elsewhere (possibly our own earlier session)
                        loggedIn = true; loginAtMs = System.currentTimeMillis()
                        fetchSesTok(); return
                    }
                    "108007" -> {
                        lockoutUntilMs = System.currentTimeMillis() + 120_000
                        throw e
                    }
                    "108006", "108001" -> { /* wrong scheme or wrong password: try next scheme once */ }
                    else -> throw e
                }
            }
        }
        throw lastErr ?: HilinkException("LOGIN", "Login gagal")
    }

    private fun attemptLogin(sch: Int) {
        fetchSesTok() // fresh token + fresh cookies for EVERY attempt (tokens can be single-use)
        val finalHash = if (sch == 4) com.netaver.hilink.core.HashKit.scheme4(username, password, token)
            else com.netaver.hilink.core.HashKit.inner(password)
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<Username>" + esc(username) + "</Username>" +
            "<Password>" + finalHash + "</Password>" +
            "<password_type>" + sch + "</password_type></request>"
        val r = raw("POST", "/api/user/login", body)
        val code = r["__code"]
        if (code != null) throw HilinkException(code, Errors.describe(code))
        // some firmwares return the fresh session inside the login response body
        val ses = r["SesInfo"]
        if (!ses.isNullOrEmpty()) sessionId = if (ses.startsWith("SessionID=")) ses.substring(10) else ses
        val tok = r["TokInfo"]
        if (!tok.isNullOrEmpty()) token = tok
    }

    @Synchronized
    fun logout() {
        try { raw("POST", "/api/user/logout", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request></request>") } catch (e: Exception) { }
        sessionId = ""; token = ""; loggedIn = false
    }

    /** Reachability + login requirements WITHOUT spending a password attempt. */
    @Synchronized
    fun testConnection(): Reach {
        return try {
            fetchSesTok()
            val st = raw("GET", "/api/user/state-login", null)
            Reach(true, st["LoginState"] ?: "", st["password_type"]?.toIntOrNull() ?: 4,
                if (token.isNotEmpty() || sessionId.isNotEmpty()) "Router menjawab, token sesi didapat." else "Router menjawab.")
        } catch (e: Exception) {
            Reach(false, "", 4, "Tidak terjangkau: ${e.message}")
        }
    }

    private fun fetchSesTok() {
        val m = raw("GET", "/api/webserver/SesTokInfo", null)
        val ses = m["SesInfo"]
        if (!ses.isNullOrEmpty()) sessionId = if (ses.startsWith("SessionID=")) ses.substring(10) else ses
        val tok = m["TokInfo"]
        if (!tok.isNullOrEmpty()) token = tok
        if (token.isEmpty()) { // modded WebUI fallback
            try {
                val t = raw("GET", "/api/webserver/token", null)
                val tv = t["token"] ?: t["TokInfo"]
                if (!tv.isNullOrEmpty()) token = tv
            } catch (e: Exception) { }
        }
    }

    // ---------------- request plumbing ----------------

    private fun authed(method: String, path: String, body: String?): Map<String, String> {
        var r = raw(method, path, body)
        var code = r["__code"]
        if (code != null && Errors.isSessionError(code)) {
            loggedIn = false
            loginLocked(manual = false)
            r = raw(method, path, body)
            code = r["__code"]
        }
        if (code != null) throw HilinkException(code, Errors.describe(code))
        return r
    }

    /** Public read; if the router demands a session anyway, log in once and retry. */
    private fun pubGet(path: String): Map<String, String> {
        val r = raw("GET", path, null)
        val code = r["__code"]
        if (code != null && Errors.isSessionError(code)) return authed("GET", path, null)
        if (code != null) throw HilinkException(code, Errors.describe(code))
        return r
    }

    @Synchronized
    private fun raw(method: String, path: String, body: String?): Map<String, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.requestMethod = method
        c.setRequestProperty("Accept", "*/*")
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) NetAverHiLink/2.0")
        val cookieParts = LinkedHashMap<String, String>()
        for ((k, v) in cookieJar) cookieParts[k] = v
        if (sessionId.isNotEmpty() && !cookieParts.containsKey("SessionID")) cookieParts["SessionID"] = sessionId
        if (cookieParts.isNotEmpty()) {
            c.setRequestProperty("Cookie", cookieParts.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        if (token.isNotEmpty()) c.setRequestProperty("__RequestVerificationToken", token)
        // modded WebUIs sometimes issue a fresh token per response header
        if (body != null) {
            val b = body.toByteArray(Charsets.UTF_8)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml")
            c.setRequestProperty("Content-Length", b.size.toString())
            c.outputStream.use { it.write(b) }
        }
        val rc = c.responseCode
        // harvest cookies (modded WebUIs bind the token to a cookie session)
        for ((hk, values) in c.headerFields) {
            if (hk != null && hk.equals("Set-Cookie", ignoreCase = true)) {
                for (hv in values) {
                    val pair = hv.substringBefore(';')
                    val name = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=', "").trim()
                    if (name.isNotEmpty()) cookieJar[name] = value
                }
            }
        }
        cookieJar["SessionID"]?.let { if (it.isNotEmpty()) sessionId = it }
        val newTok = c.getHeaderField("__RequestVerificationToken")
        if (!newTok.isNullOrEmpty()) token = newTok
        val stream: InputStream? = if (rc >= 400) c.errorStream else c.inputStream
        val xml = stream?.use { readAll(it) } ?: ""
        c.disconnect()
        return parseXml(xml)
    }

    // ---------------- data API ----------------

    fun status(): Status {
        val m = pubGet("/api/monitoring/status")
        return Status(v(m, "ConnectionStatus"), v(m, "SignalIcon"), v(m, "CurrentNetworkType"),
            v(m, "RoamingStatus"), v(m, "SimStatus"),
            v(m, "BatteryPercent").ifEmpty {
                // BatteryLevel on this firmware family is a 0..4 BAR scale, not a percent (4 = full = 100%).
                v(m, "BatteryLevel").toIntOrNull()?.let { (it * 25).coerceAtMost(100).toString() } ?: ""
            }, v(m, "BatteryStatus"))
    }

    /** Modded-WebUI daemon data (band/EARFCN/frequency from AT^HFREQINFO). Throws on stock firmware. */
    fun addParam(): Map<String, String> = pubGet("/config/deviceinformation/add_param.xml")

    /** Raw /api/device/signal map with NO fallback chain — for the 1-second pointing poll. */
    fun signalLight(): Map<String, String> = try { authed("GET", "/api/device/signal", null) } catch (e: Exception) { try { pubGet("/api/device/signal") } catch (e2: Exception) { emptyMap() } }

    /** Raw /api/net/current-plmn map (Numeric / ShortName / FullName). */
    fun plmnInfo(): Map<String, String> = try { pubGet("/api/net/current-plmn") } catch (e: Exception) { emptyMap() }

    fun signal(): Signal {
        val m = try { authed("GET", "/api/device/signal", null) } catch (e: Exception) { pubGet("/api/device/signal") }
        var pci = v(m, "pci")
        var band = resolveBand(m)
        if (band.isEmpty()) com.netaver.hilink.core.Format.bandFromRawXml(m["__raw"] ?: "")?.let { band = it.toString() }
        if (pci.isEmpty() || band.isEmpty()) {
            for (path in listOf("/api/net/cell-info", "/api/device/cell-info")) {
                try {
                    val ci = try { authed("GET", path, null) } catch (e: Exception) { pubGet(path) }
                    if (pci.isEmpty()) pci = v(ci, "pci")
                    if (band.isEmpty()) band = resolveBand(ci)
                    if (band.isEmpty()) com.netaver.hilink.core.Format.bandFromRawXml(ci["__raw"] ?: "")?.let { band = it.toString() }
                    if (pci.isNotEmpty() && band.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }
        if (band.isEmpty()) {
            // Modded WebUI (ilya-fedin Mod1.21 family): the add_param daemon collects band/EARFCN via
            // AT^HFREQINFO and serves them at /config/deviceinformation/add_param.xml (login-free).
            try {
                val ap = pubGet("/config/deviceinformation/add_param.xml")
                band = resolveBand(ap)
                if (band.isEmpty()) com.netaver.hilink.core.Format.bandFromRawXml(ap["__raw"] ?: "")?.let { band = it.toString() }
            } catch (e: Exception) { /* stock firmware: no such file, band stays unknown */ }
        }
        return Signal(
            com.netaver.hilink.core.Format.stripUnit(v(m, "rssi")),
            com.netaver.hilink.core.Format.stripUnit(v(m, "rsrp")),
            com.netaver.hilink.core.Format.stripUnit(v(m, "rsrq")),
            com.netaver.hilink.core.Format.stripUnit(v(m, "sinr")),
            com.netaver.hilink.core.Format.stripUnit(v(m, "rscp")),
            com.netaver.hilink.core.Format.stripUnit(v(m, "ecio")),
            v(m, "cell_id"), pci, band)
    }

    /** Serving band from a signal/cell-info payload: explicit band element (any format), else EARFCN math. */
    private fun resolveBand(m: Map<String, String>): String {
        val rawBand = ci(m, "band")
        if (rawBand.isNotEmpty()) {
            com.netaver.hilink.core.Format.firstInt(rawBand)?.let { if (it in 1..88) return it.toString() }
        }
        for (k in listOf("earfcn", "earfcn1", "earfcn2", "dl_earfcn", "earfcndl", "dlEarfcn", "EarfcnDl")) {
            val e = ci(m, k).toIntOrNull()
            if (e != null) com.netaver.hilink.core.Format.bandFromEarfcn(e)?.let { return it.toString() }
        }
        return ""
    }

    fun operatorName(): String {
        return try {
            val m = pubGet("/api/net/current-plmn")
            v(m, "ShortName").ifEmpty { v(m, "FullName") }
        } catch (e: Exception) { "" }
    }

    fun traffic(): Traffic {
        val m = pubGet("/api/monitoring/traffic-statistics")
        return Traffic(num(m, "CurrentUpload"), num(m, "CurrentDownload"), num(m, "TotalUpload"),
            num(m, "TotalDownload"), num(m, "CurrentUploadRate"), num(m, "CurrentDownloadRate"),
            num(m, "CurrentConnectTime"))
    }

    fun monthStat(): MonthStat {
        val m = pubGet("/api/monitoring/month_statistics")
        return MonthStat(num(m, "CurrentMonthDownload"), num(m, "CurrentMonthUpload"))
    }

    fun bootTimeSec(): Long = try { num(pubGet("/api/device/boot_time"), "boot_time") } catch (e: Exception) { 0 }

    fun unreadSms(): Int = try {
        pubGet("/api/monitoring/check-notifications")["UnreadMessage"]?.toIntOrNull() ?: 0
    } catch (e: Exception) { 0 }

    fun deviceInfo(): DeviceInfo {
        var m = authed("GET", "/api/device/information", null)
        if (m.size <= 1) m = authed("POST", "/api/device/information", null)
        return DeviceInfo(v(m, "DeviceName"), v(m, "SerialNumber"), v(m, "Imei"),
            v(m, "HardwareVersion"), v(m, "SoftwareVersion"),
            v(m, "MacAddress1").ifEmpty { v(m, "MacAddressWiFi") })
    }

    fun hosts(): List<Host> {
        val out = mutableListOf<Host>()
        try {
            val r = authed("GET", "/api/wlan/host-list", null)
            for (b in blocks(r["__raw"] ?: "", "Host")) {
                val mm = parseXml(b)
                out.add(Host(v(mm, "HostName"), v(mm, "IpAddress"), v(mm, "MacAddress"), v(mm, "Active")))
            }
        } catch (e: Exception) { }
        return out
    }

    fun netMode(): NetMode {
        var m = authed("GET", "/api/net/net-mode", null)
        if (m.size <= 1) m = authed("POST", "/api/net/net-mode", null)
        return NetMode(v(m, "NetworkMode"), v(m, "NetworkBand"), v(m, "LTEBand"))
    }

    fun setNetMode(mode: String, networkBand: String, lteBandHex: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<NetworkMode>$mode</NetworkMode><NetworkBand>$networkBand</NetworkBand>" +
            "<LTEBand>${lteBandHex.uppercase()}</LTEBand></request>"
        authed("POST", "/api/net/net-mode", body)
    }

    fun setDataSwitch(on: Boolean) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><dataswitch>${if (on) 1 else 0}</dataswitch></request>"
        authed("POST", "/api/dialup/mobile-dataswitch", body)
    }

    /** 1 = reboot, 2 = factory restore, 4 = power off. */
    fun deviceControl(action: Int) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Control>$action</Control></request>"
        authed("POST", "/api/device/control", body)
    }

    fun clearTraffic() {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><ClearTraffic>1</ClearTraffic></request>"
        authed("POST", "/api/monitoring/clear-traffic", body)
    }

    /** Returns (ant1, ant2) types or null when the modem has no antenna API (e.g. MiFi). */
    fun antennaTypes(): Pair<String, String>? {
        return try {
            val m = authed("GET", "/api/device/antenna_type", null)
            val a1 = v(m, "antenna1type"); val a2 = v(m, "antenna2type")
            if (a1.isEmpty() && a2.isEmpty()) null else a1 to a2
        } catch (e: Exception) { null }
    }

    fun setAntenna(a1: String, a2: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<antenna1type>$a1</antenna1type><antenna2type>$a2</antenna2type></request>"
        authed("POST", "/api/device/antenna_set_type", body)
    }

    /** Operator scan (read-only). Returns display strings "Nama (PLMN) · RAT". */
    fun plmnScan(): List<String> {
        val r = authed("GET", "/api/net/plmn-list", null)
        val out = mutableListOf<String>()
        for (b in blocks(r["__raw"] ?: "", "Network")) {
            val mm = parseXml(b)
            val name = v(mm, "ShortName").ifEmpty { v(mm, "FullName") }.ifEmpty { v(mm, "OperatorName") }
            val plmn = v(mm, "PLMN"); val rat = v(mm, "Rat")
            val ratLabel = when (rat) { "7" -> "4G"; "2" -> "3G"; "0" -> "2G"; else -> rat }
            out.add(listOf(name.ifEmpty { plmn }, if (plmn.isNotEmpty()) "($plmn)" else "", ratLabel)
                .filter { it.isNotEmpty() }.joinToString(" "))
        }
        return out
    }

    // ---------------- Router settings: WLAN ----------------

    fun wlanBasic(): Map<String, String> = authed("GET", "/api/wlan/basic-settings", null)
    fun wlanBasicRaw(): String = authed("GET", "/api/wlan/basic-settings", null)["__raw"] ?: ""

    /** Post a full read-modify-write basic-settings body (WebUI-style). */
    fun setWlanBasicRaw(requestBody: String) {
        authed("POST", "/api/wlan/basic-settings", requestBody)
    }
    fun wlanSecurity(): Map<String, String> = authed("GET", "/api/wlan/security-settings", null)

    fun setWlanBasic(ssid: String, hide: Boolean, enable: String) {
        var body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<WifiSsid>" + esc(ssid) + "</WifiSsid>" +
            "<WifiHide>" + hide.toString() + "</WifiHide>"
        if (enable.isNotEmpty()) body += "<WifiEnable>" + esc(enable) + "</WifiEnable>"
        body += "<WifiRestart>1</WifiRestart></request>"
        authed("POST", "/api/wlan/basic-settings", body)
    }

    /** Only the auth mode (and PSK when given) change; encryption fields are written back as read. */
    fun setWlanSecurity(authMode: String, psk: String?) {
        val cur = wlanSecurity()
        var body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<WifiAuthmode>" + esc(authMode) + "</WifiAuthmode>"
        if (psk != null) body += "<WifiWpapsk>" + esc(psk) + "</WifiWpapsk>"
        val enc = ci(cur, "WifiWpaencryptionmodes")
        val basic = ci(cur, "WifiBasicencryptionmodes")
        val wep = ci(cur, "WifiWepKey1")
        body += "<WifiWpaencryptionmodes>" + esc(enc.ifEmpty { if (authMode == "WPA2-PSK") "AES" else "MIX" }) + "</WifiWpaencryptionmodes>"
        body += "<WifiBasicencryptionmodes>" + esc(basic.ifEmpty { if (authMode == "OPEN") "NONE" else "WEP128" }) + "</WifiBasicencryptionmodes>"
        if (wep.isNotEmpty()) body += "<WifiWepKey1>" + esc(wep) + "</WifiWepKey1>"
        body += "<WifiRestart>1</WifiRestart></request>"
        authed("POST", "/api/wlan/security-settings", body)
    }

    /** Raw multi-profile WLAN settings (api/wlan/multi-basic-settings), as the WebUI's
     *  wlanadvanced page reads them: response.Ssids.Ssid[] profile objects. */
    fun wlanMultiBasicRaw(): String = authed("GET", "/api/wlan/multi-basic-settings", null)["__raw"] ?: ""

    /** Post the full profile array back (read-modify-write, WebUI-style). */
    fun setWlanMultiBasic(requestBody: String) {
        authed("POST", "/api/wlan/multi-basic-settings", requestBody)
    }

    // ---------------- Router settings: DHCP ----------------

    fun dhcpSettings(): Map<String, String> = authed("GET", "/api/dhcp/settings", null)

    /** Merge overrides into the settings the router itself reported, then post the known keys back.
     *  Key names are the firmware's own (WebUI dhcp.js): mask = DhcpLanNetmask,
     *  server on/off = DhcpStatus, DNS mode = DnsStatus (0 manual, 1 automatic).
     *  An override that is present is posted even when empty — clearing both DNS
     *  fields must send empty elements, exactly like the WebUI, so the router
     *  drops the stored manual DNS when switching back to automatic. */
    fun setDhcp(overrides: Map<String, String>) {
        val cur = dhcpSettings()
        val keys = listOf("DhcpIPAddress", "DhcpLanNetmask", "DhcpStatus", "DhcpStartIPAddress",
            "DhcpEndIPAddress", "DhcpLeaseTime", "PrimaryDns", "SecondaryDns", "DnsStatus")
        var body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>"
        for (k in keys) {
            if (overrides.containsKey(k)) {
                body += "<" + k + ">" + esc(overrides[k] ?: "") + "</" + k + ">"
            } else {
                val value = ci(cur, k)
                if (value.isNotEmpty()) body += "<" + k + ">" + esc(value) + "</" + k + ">"
            }
        }
        body += "</request>"
        authed("POST", "/api/dhcp/settings", body)
    }

    // ---------------- Router settings: USSD ----------------

    /**
     * USSD flow mirrors the router WebUI's own ussd.js (recovered from the mod
     * package's ussd.js.gz on 2026-10-21):
     *  - a USSD "dialog" can dangle on the modem (ussd/status result == 1);
     *    the WebUI closes it via GET /api/ussd/release — always release first.
     *  - send exactly ONE textbook body {content, codeType: "CodeType", timeout: ""}.
     *  - error 111019 from /api/ussd/get means "no response from network (yet)" —
     *    the WebUI keeps polling until its 2-minute timer; it is NOT a rejection.
     */
    fun ussdRelease() {
        try {
            authed("GET", "/api/ussd/release", null)
        } catch (e: Exception) {
            // best effort only — a missing/failed release must not block the flow
        }
    }

    fun ussdSend(content: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<content>" + esc(content) + "</content>" +
            "<codeType>CodeType</codeType><timeout></timeout></request>"
        authed("POST", "/api/ussd/send", body)
    }

    fun ussdGet(): Map<String, String> = authed("GET", "/api/ussd/get", null)

    fun ussdStatus(): Map<String, String> = authed("GET", "/api/ussd/status", null)

    // ---------------- Router settings: APN profiles ----------------

    data class ApnProfile(
        val index: String, val name: String, val apn: String, val username: String,
        val password: String, val authMode: String, val readOnly: String,
    )

    fun apnProfiles(): Pair<List<ApnProfile>, String> {
        val m = authed("GET", "/api/dialup/profiles", null)
        val list = blocks(m["__raw"] ?: "", "Profile").map { blk ->
            val mm = parseXml(blk)
            ApnProfile(ci(mm, "Index"), ci(mm, "Name"), ci(mm, "ApnName"), ci(mm, "Username"),
                ci(mm, "Password"), ci(mm, "AuthMode"), ci(mm, "ReadOnly"))
        }
        return Pair(list, ci(m, "CurrentProfile"))
    }

    fun setDefaultProfile(index: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<SetDefault>" + esc(index) + "</SetDefault><Delete>0</Delete><Modify>0</Modify></request>"
        authed("POST", "/api/dialup/profiles", body)
    }

    fun deleteProfile(index: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<SetDefault>0</SetDefault><Delete>" + esc(index) + "</Delete><Modify>0</Modify></request>"
        authed("POST", "/api/dialup/profiles", body)
    }

    fun saveProfile(p: ApnProfile, isNew: Boolean) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<SetDefault>0</SetDefault><Delete>0</Delete><Modify>1</Modify>" +
            "<Profile>" +
            "<Index>" + esc(if (isNew) "" else p.index) + "</Index>" +
            "<IsValid>1</IsValid>" +
            "<Name>" + esc(p.name) + "</Name>" +
            "<ApnIsStatic>" + (if (p.apn.isNotEmpty()) "1" else "0") + "</ApnIsStatic>" +
            "<ApnName>" + esc(p.apn) + "</ApnName>" +
            "<DialupNum>*99#</DialupNum>" +
            "<Username>" + esc(p.username) + "</Username>" +
            "<Password>" + esc(p.password) + "</Password>" +
            "<AuthMode>" + esc(p.authMode) + "</AuthMode>" +
            "<IpIsStatic></IpIsStatic><IpAddress></IpAddress>" +
            "<DnsIsStatic></DnsIsStatic><PrimaryDns></PrimaryDns><SecondaryDns></SecondaryDns>" +
            "<ReadOnly>0</ReadOnly><iptype>0</iptype>" +
            "</Profile></request>"
        authed("POST", "/api/dialup/profiles", body)
    }

    /** A single configurable raw request for response-shape experiments. Never throws; returns scrubbed XML or an ERROR line. */
    private fun rawX(method: String, path: String, body: String?, headers: Map<String, String>,
                     useJar: Boolean, tok: String?): String {
        return try {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.requestMethod = method
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) NetAverHiLink/2.0")
            if (useJar) {
                val parts = LinkedHashMap<String, String>()
                for ((k, v2) in cookieJar) parts[k] = v2
                if (sessionId.isNotEmpty() && !parts.containsKey("SessionID")) parts["SessionID"] = sessionId
                if (parts.isNotEmpty()) c.setRequestProperty("Cookie", parts.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (tok != null) c.setRequestProperty("__RequestVerificationToken", tok)
            for ((k, v2) in headers) c.setRequestProperty(k, v2)
            if (body != null) {
                val b = body.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "text/xml")
                c.setRequestProperty("Content-Length", b.size.toString())
                c.outputStream.use { it.write(b) }
            }
            val rc = c.responseCode
            val stream: InputStream? = if (rc >= 400) c.errorStream else c.inputStream
            val xml = stream?.use { readAll(it) } ?: ""
            c.disconnect()
            val m = parseXml(xml)
            val code = m["__code"]
            if (code != null) "ERROR (kode $code): ${Errors.describe(code)}\n" + scrubXml(xml).take(1500)
            else scrubXml(xml).take(1500)
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    /**
     * The mod WebUI shows band/EARFCN/tx-power that the plain signal response hides on this firmware.
     * Try the plausible request shapes and report each raw answer so the unlocking variant (if any) is visible.
     */
    fun signalVariants(): List<Pair<String, String>> {
        val referer = mapOf("Referer" to "http://$host/html/deviceinformation.html", "Origin" to "http://$host")
        val xhr = referer + ("X-Requested-With" to "XMLHttpRequest")
        val freshTok = try { fetchSesTok(); token } catch (e: Exception) { token }
        val emptyReq = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request></request>"
        return listOf(
            "kontrol: GET sesi saat ini" to rawX("GET", "/api/device/signal", null, emptyMap(), true, token),
            "GET + Referer WebUI" to rawX("GET", "/api/device/signal", null, referer, true, token),
            "GET + Referer + XHR" to rawX("GET", "/api/device/signal", null, xhr, true, token),
            "POST kosong + Referer" to rawX("POST", "/api/device/signal", emptyReq, referer, true, token),
            "GET polos tanpa sesi" to rawX("GET", "/api/device/signal", null, emptyMap(), false, null),
            "GET token segar saja (gaya eyade)" to rawX("GET", "/api/device/signal", null, emptyMap(), false, freshTok),
            "GET cell-info + Referer" to rawX("GET", "/api/net/cell-info", null, referer, true, token),
            "POST cell-info + Referer" to rawX("POST", "/api/net/cell-info", emptyReq, referer, true, token)
        )
    }

    /** Fetch a text resource (page/JS) from the router with the session applied. Returns (httpCode, body-capped). */
    private fun fetchText(path: String, cap: Int): Pair<Int, String> {
        return try {
            val url = if (path.startsWith("http")) path else base + path
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.requestMethod = "GET"
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) NetAverHiLink/2.0")
            val parts = LinkedHashMap<String, String>()
            for ((k, v2) in cookieJar) parts[k] = v2
            if (sessionId.isNotEmpty() && !parts.containsKey("SessionID")) parts["SessionID"] = sessionId
            if (parts.isNotEmpty()) c.setRequestProperty("Cookie", parts.entries.joinToString("; ") { "${it.key}=${it.value}" })
            if (token.isNotEmpty()) c.setRequestProperty("__RequestVerificationToken", token)
            val rc = c.responseCode
            val stream: InputStream? = if (rc >= 400) c.errorStream else c.inputStream
            val text = stream?.use { readAll(it) } ?: ""
            c.disconnect()
            rc to text.take(cap)
        } catch (e: Exception) {
            -1 to "ERROR: ${e.message}"
        }
    }

    /**
     * The mod WebUI renders band/EARFCN that the standard API hides — its own JS, served by the router,
     * must call something. Fetch the UI pages + their scripts and extract every api/ path plus any
     * band/earfcn-bearing fragments.
     */
    fun webUiProbe(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val apiPath = Regex("api/[a-zA-Z0-9/_\\-\\.]+")
        val srcRe = Regex("src\\s*=\\s*\"([^\"]+\\.js[^\"]*)\"")
        val pages = listOf("/html/deviceinformation.html", "/html/home.html", "/html/index.html",
            "/html/content.html", "/index.html", "/")
        val allApis = LinkedHashSet<String>()
        val fragments = ArrayList<String>()
        var scriptsFetched = 0
        for (page in pages) {
            val (rc, body) = fetchText(page, 300_000)
            if (rc != 200 || body.isEmpty()) {
                out.add("halaman $page" to "HTTP $rc")
                continue
            }
            apiPath.findAll(body).forEach { allApis.add(it.value) }
            out.add("halaman $page" to "HTTP 200, ${body.length} bita, ${srcRe.findAll(body).count()} skrip")
            val dir = page.substringBeforeLast('/') + "/"
            for (m in srcRe.findAll(body)) {
                if (scriptsFetched >= 8) break
                var src = m.groupValues[1]
                if (src.startsWith("http")) continue
                if (!src.startsWith("/")) src = dir + src
                val (jrc, js) = fetchText(src, 400_000)
                scriptsFetched++
                if (jrc != 200) {
                    out.add("js $src" to "HTTP $jrc")
                    continue
                }
                apiPath.findAll(js).forEach { allApis.add(it.value) }
                var fragCount = 0
                for (frag in js.split(';', '\n')) {
                    val t = frag.trim()
                    if (t.length in 8..220 && (t.contains("earfcn", true) || t.contains("band", true)) && fragCount < 30) {
                        fragments.add(t)
                        fragCount++
                    }
                }
                out.add("js $src" to "HTTP 200, ${js.length} bita, $fragCount cuplikan band/earfcn")
            }
            if (scriptsFetched > 0) break
        }
        out.add("endpoint api unik dari JS WebUI" to allApis.sorted().joinToString(", ").ifEmpty { "(tidak ditemukan)" })
        out.add("cuplikan band/earfcn" to fragments.joinToString("\n").ifEmpty { "(tidak ada)" })
        return out
    }

    /** Raw XML of the engineering endpoints, for diagnosing what this firmware actually publishes. */
    fun rawDump(): String {
        val sb = StringBuilder()
        val targets = listOf(
            "device/signal", "net/cell-info", "device/cell-info",
            "monitoring/status", "net/net-mode", "device/information", "ussd/status", "ussd/get"
        )
        for (t in targets) {
            for (mode in listOf("pub", "auth")) {
                try {
                    val m = if (mode == "pub") pubGet("/api/$t") else authed("GET", "/api/$t", null)
                    val raw = scrubXml(m["__raw"] ?: "").take(1800)
                    sb.append("=== /api/$t [$mode] ===\n").append(raw).append("\n\n")
                } catch (e: Exception) {
                    val code = (e as? HilinkException)?.code
                    sb.append("=== /api/$t [$mode] === ERROR")
                    if (code != null) sb.append(" (kode ").append(code).append(")")
                    sb.append(": ").append(e.message).append("\n\n")
                }
            }
        }
        return sb.toString()
    }

    // ---------------- SMS ----------------

    fun smsList(page: Int = 1, count: Int = 20): List<Sms> {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<PageIndex>$page</PageIndex><ReadCount>$count</ReadCount>" +
            "<BoxType>1</BoxType><SortType>0</SortType><Ascending>0</Ascending>" +
            "<UnreadPreferred>0</UnreadPreferred></request>"
        val m = authed("POST", "/api/sms/sms-list", body)
        val out = mutableListOf<Sms>()
        for (b in blocks(m["__raw"] ?: "", "Message")) {
            val mm = parseXml(b)
            out.add(Sms(v(mm, "Index"), v(mm, "Phone"), v(mm, "Content"), v(mm, "Date"), v(mm, "Smstat")))
        }
        return out
    }

    fun smsSend(phone: String, content: String) {
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<Index>-1</Index><Phones><Phone>${esc(phone)}</Phone></Phones><Sca></Sca>" +
            "<Content>${esc(content)}</Content><Length>${content.length}</Length>" +
            "<Reserved>1</Reserved><Date>-1</Date></request>"
        authed("POST", "/api/sms/send-sms", body)
    }

    fun smsDelete(index: String) {
        authed("POST", "/api/sms/delete-sms",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Index>${esc(index)}</Index></request>")
    }

    fun smsMarkRead(index: String) {
        try {
            authed("POST", "/api/sms/set-read",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Index>${esc(index)}</Index></request>")
        } catch (e: Exception) { }
    }

    // ---------------- helpers ----------------

    companion object {
        /** Case-insensitive lookup (firmwares differ in element capitalization). */
        private val SENSITIVE_TAG = Regex("(?i)(<(?:imei|imsi|iccid|msisdn|serialnumber|macaddress\\w*|phonenumber)>)[^<]*")
        fun scrubXml(s: String): String = SENSITIVE_TAG.replace(s) { it.groupValues[1] + "***" }

        fun ci(m: Map<String, String>, key: String): String {
            m[key]?.let { return it }
            val lk = key.lowercase()
            for ((k, v) in m) if (k.lowercase() == lk) return v
            return ""
        }

        fun v(m: Map<String, String>, k: String): String = m[k] ?: ""
        fun num(m: Map<String, String>, k: String): Long = v(m, k).toLongOrNull() ?: 0
        fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        fun readAll(input: InputStream): String {
            val buf = java.io.ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(tmp)
                if (n <= 0) break
                total += n
                if (total > 2_000_000) break // hardening: cap router responses at 2 MB
                buf.write(tmp, 0, n)
            }
            return String(buf.toByteArray(), Charsets.UTF_8)
        }

        /** Extract repeated <tag>...</tag> blocks from raw XML. */
        fun blocks(raw: String, tag: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            val open = "<$tag>"; val close = "</$tag>"
            while (true) {
                val a = raw.indexOf(open, i); if (a < 0) break
                val b = raw.indexOf(close, a); if (b < 0) break
                out.add(raw.substring(a, b + close.length))
                i = b + close.length
            }
            return out
        }

        /** Flat XML -> map (depth-2 tags), plus __code from <error><code>, plus __raw. */
        fun parseXml(xml: String): Map<String, String> {
            val m = HashMap<String, String>()
            if (xml.isEmpty()) return m
            m["__raw"] = xml
            try {
                val p: XmlPullParser = Xml.newPullParser()
                p.setInput(java.io.StringReader(xml))
                var cur: String? = null
                val txt = StringBuilder()
                var depth = 0
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            depth++
                            cur = when {
                                p.name == "code" && depth == 2 -> "__code"
                                depth == 2 && p.name != "Message" && p.name != "Messages" &&
                                    p.name != "Host" && p.name != "Hosts" && p.name != "Network" -> p.name
                                else -> null
                            }
                            txt.setLength(0)
                        }
                        XmlPullParser.TEXT -> if (cur != null) txt.append(p.text)
                        XmlPullParser.END_TAG -> {
                            if (cur != null && depth <= 3) m[cur] = txt.toString().trim()
                            if (depth == 2) cur = null
                            depth--
                        }
                    }
                    ev = p.next()
                }
            } catch (e: Exception) { }
            return m
        }
    }
}

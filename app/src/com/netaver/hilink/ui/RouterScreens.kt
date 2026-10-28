package com.netaver.hilink.ui

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.netaver.hilink.core.LanGuard
import com.netaver.hilink.core.UssdText
import com.netaver.hilink.net.HilinkClient
import com.netaver.hilink.net.HilinkClient.HilinkException

// ---- shared scaffolding (mirrors the existing screens' idioms) ----

private fun subPage(act: MainActivity, title: String, sub: String): Pair<ScrollView, LinearLayout> {
    val ctx = act
    val scroll = ScrollView(ctx); scroll.setBackgroundColor(Ui.BG)
    val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
    root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
    scroll.addView(root)
    val back = Ui.text(ctx, "‹  Pengaturan Router", 15f, Ui.ACCENT, true)
    back.setPadding(0, 0, 0, Ui.dp(ctx, 10))
    back.setOnClickListener { act.show(MainActivity.TAB_SETTINGS) }
    root.addView(back)
    root.addView(Ui.text(ctx, title, 24f, Ui.TXT, true))
    val s = Ui.text(ctx, sub, 13.5f, Ui.SUB)
    s.setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 12))
    root.addView(s)
    return Pair(scroll, root)
}

private fun fldLabel(act: MainActivity, s: String): TextView {
    val t = Ui.text(act, s, 12f, Ui.SUB, true)
    t.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 6))
    return t
}

private fun fld(act: MainActivity, hint: String, password: Boolean = false): EditText {
    val e = EditText(act)
    e.hint = hint
    e.setTextColor(Ui.TXT); e.setHintTextColor(Ui.FAINT); e.textSize = 15.5f
    if (password) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    e.background = Ui.bg(Color.rgb(13, 20, 36), 10, act, Ui.BORDER)
    e.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = Ui.dp(act, 4)
    e.layoutParams = lp
    return e
}

private fun gapView(act: MainActivity, h: Int): View {
    val v = View(act)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, h))
    return v
}

private fun noteView(act: MainActivity, s: String): TextView {
    val t = Ui.text(act, s, 12.5f, Ui.SUB)
    t.setPadding(0, Ui.dp(act, 8), 0, 0)
    t.setLineSpacing(0f, 1.35f)
    return t
}

/** Single-select chip row; rebuilds itself on selection. */
private class ChipRow(
    private val act: MainActivity,
    private val names: List<String>,
    private val onPick: (Int) -> Unit,
) : LinearLayout(act) {
    var sel = 0
        private set

    init {
        orientation = HORIZONTAL
        render()
    }

    fun select(i: Int) {
        sel = i
        render()
    }

    private fun render() {
        removeAllViews()
        names.forEachIndexed { i, n ->
            val c = Ui.chip(act, n, i == sel)
            c.setOnClickListener { select(i); onPick(i) }
            val lp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(act, 8)
            addView(c, lp)
        }
    }
}

/** Read one element's text from an XML block string ("" when absent). */
private fun xmlField(block: String, name: String): String {
    val open = "<$name>"; val close = "</$name>"
    val a = block.indexOf(open); if (a < 0) return ""
    val b = block.indexOf(close, a + open.length); if (b < 0) return ""
    return block.substring(a + open.length, b).trim()
}

/** Replace one element's text inside an XML block string; block untouched when absent.
 *  This is what makes the WLAN advanced write safe: every other byte of the router's
 *  own profile (SSID, country, unknown fields) is posted back exactly as read. */
private fun patchXmlField(block: String, name: String, value: String): String {
    val open = "<$name>"; val close = "</$name>"
    val a = block.indexOf(open); if (a < 0) return block
    val b = block.indexOf(close, a + open.length); if (b < 0) return block
    return block.substring(0, a + open.length) + HilinkClient.esc(value) + block.substring(b)
}

/** Single-select chip grid that wraps into rows of [perRow]; for long lists (channels). */
private class ChipGrid(
    private val act: MainActivity,
    private val names: List<String>,
    private val perRow: Int,
    private val onPick: (Int) -> Unit,
) : LinearLayout(act) {
    var sel = 0
        private set

    init {
        orientation = VERTICAL
        render()
    }

    fun select(i: Int) {
        sel = i
        render()
    }

    private fun render() {
        removeAllViews()
        names.chunked(perRow).forEachIndexed { r, chunk ->
            val row = LinearLayout(act); row.orientation = HORIZONTAL
            if (r > 0) row.setPadding(0, Ui.dp(act, 8), 0, 0)
            chunk.forEachIndexed { j, n ->
                val idx = r * perRow + j
                val c = Ui.chip(act, n, idx == sel)
                c.setOnClickListener { select(idx); onPick(idx) }
                val lp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (j > 0) lp.leftMargin = Ui.dp(act, 8)
                row.addView(c, lp)
            }
            for (k in chunk.size until perRow) {
                val spacer = View(act)
                spacer.layoutParams = LayoutParams(0, 1, 1f)
                if (k > 0) spacer.layoutParams = (spacer.layoutParams as LayoutParams).apply { leftMargin = Ui.dp(act, 8) }
                row.addView(spacer)
            }
            addView(row)
        }
    }
}

private fun switchRow(act: MainActivity, card: LinearLayout, label: String): Switch {
    val row = LinearLayout(act); row.orientation = LinearLayout.HORIZONTAL
    val t = Ui.text(act, label, 16f, Ui.TXT)
    t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    row.addView(t)
    val sw = Switch(act)
    row.addView(sw)
    card.addView(row)
    return sw
}

private val CH24_BASE: List<String> = listOf("0") + (1..13).map { it.toString() }
private val CH5_BASE: List<String> = listOf(
    "0", "36", "40", "44", "48", "52", "56", "60", "64",
    "100", "104", "108", "112", "116", "120", "124", "128", "132", "136", "140", "144",
    "149", "153", "157", "161", "165",
)
private val BW_VALS: List<String> = listOf("0", "20", "40")

// ---------------- WiFi ----------------

class WifiScreen(private val act: MainActivity) : Screen {
    private lateinit var status: TextView
    private lateinit var ssidIn: EditText
    private lateinit var pskIn: EditText
    private lateinit var hideSw: Switch
    private lateinit var enableSw: Switch
    private lateinit var isolateSw: Switch
    private lateinit var authRow: ChipRow
    private lateinit var advBox: LinearLayout
    private var origAuth = ""
    private var enableKnown = ""

    // ---- WLAN advanced (multi-basic-settings), mirrors the WebUI wlanadvanced page ----
    private var advReady = false
    private var basicAdvReady = false
    private var basicIs5g = false
    private var advBlocks: List<String> = emptyList()
    private var advIs5g: List<Boolean> = emptyList()
    private var gridChan24: ChipGrid? = null
    private var bwRow24: ChipRow? = null
    private var modeRow24: ChipRow? = null
    private var chanVals24: List<String> = emptyList()
    private var modeVals24: List<String> = emptyList()
    private var gridChan5: ChipGrid? = null
    private var bwRow5: ChipRow? = null
    private var modeRow5: ChipRow? = null
    private var chanVals5: List<String> = emptyList()
    private var modeVals5: List<String> = emptyList()

    override fun build(actA: Activity): View {
        val ctx = act
        val (scroll, root) = subPage(act, "WiFi", "Nama jaringan, keamanan & lanjutan")
        status = Ui.text(ctx, "", 13f, Ui.SUB); root.addView(status)
        root.addView(fldLabel(act, "NAMA WIFI (SSID)"))
        ssidIn = fld(act, "Nama WiFi"); root.addView(ssidIn)
        root.addView(fldLabel(act, "PASSWORD WIFI"))
        val (pskBox, pskEdit) = Ui.passwordField(act, "Password WiFi")
        pskIn = pskEdit
        root.addView(pskBox)
        root.addView(fldLabel(act, "KEAMANAN"))
        authRow = ChipRow(act, listOf("WPA2-PSK", "WPA/WPA2", "Terbuka")) {}
        root.addView(authRow)
        root.addView(gapView(act, 10))
        val card = Ui.card(ctx)
        hideSw = switchRow(act, card, "Sembunyikan nama WiFi")
        enableSw = switchRow(act, card, "WiFi menyala")
        isolateSw = switchRow(act, card, "Isolasi AP — perangkat WiFi tidak saling melihat")
        root.addView(card)
        advBox = LinearLayout(ctx); advBox.orientation = LinearLayout.VERTICAL
        root.addView(advBox)
        root.addView(noteView(act, "Mengganti nama/password WiFi akan memutus HP dari WiFi ini — sambungkan ulang ke nama yang baru setelah diterapkan. Mengubah channel/bandwidth me-restart WiFi sesaat."))
        root.addView(gapView(act, 14))
        val save = Ui.button(ctx, "SIMPAN & TERAPKAN")
        save.setOnClickListener { confirmSave() }
        root.addView(save)
        load()
        loadAdvanced()
        return scroll
    }

    private fun is5gMode(mode: String): Boolean = mode.trim().lowercase().startsWith("a")

    private fun loadAdvanced() {
        Bg.run({
            val c = act.app().client()
            Pair(c.wlanBasic(), c.wlanMultiBasicRaw())
        }) { r ->
            r.onSuccess { (basic, raw) ->
                val blocks = HilinkClient.blocks(raw, "Ssid")
                advBlocks = blocks
                advIs5g = blocks.map { is5gMode(xmlField(it, "WifiMode")) }
                // The values that actually drive the radio (channel, bandwidth,
                // mode, isolation of the primary profile) live in basic-settings:
                // that is where the WebUI's advanced page reads them (verified in
                // its wlanadvanced.js). The multi-basic per-profile copies sit at
                // 0/auto on this firmware, which is why v2.3.0 showed "Otomatis"
                // while the router was really on ch 11 / 40 MHz.
                basicAdvReady = basic.keys.any { it.equals("WifiChannel", true) }
                if (basicAdvReady) {
                    val bMode = HilinkClient.ci(basic, "WifiMode")
                    basicIs5g = is5gMode(bMode)
                    isolateSw.isChecked = HilinkClient.ci(basic, "WifiIsolate") == "1"
                    buildBandSection(basicIs5g,
                        HilinkClient.ci(basic, "WifiChannel").ifEmpty { "0" },
                        HilinkClient.ci(basic, "wifibandwidth"), bMode)
                    val other = !basicIs5g
                    if (advIs5g.any { it == other }) {
                        val ob = advBlocks[advIs5g.indexOf(other)]
                        buildBandSection(other,
                            xmlField(ob, "WifiChannel").ifEmpty { "0" },
                            xmlField(ob, "wifibandwidth"), xmlField(ob, "WifiMode"))
                    }
                    advReady = true
                } else if (blocks.isNotEmpty()) {
                    isolateSw.isChecked = xmlField(blocks[0], "WifiIsolate") == "1"
                    if (advIs5g.any { !it }) {
                        val b0 = advBlocks[advIs5g.indexOf(false)]
                        buildBandSection(false, xmlField(b0, "WifiChannel").ifEmpty { "0" },
                            xmlField(b0, "wifibandwidth"), xmlField(b0, "WifiMode"))
                    }
                    if (advIs5g.any { it }) {
                        val b1 = advBlocks[advIs5g.indexOf(true)]
                        buildBandSection(true, xmlField(b1, "WifiChannel").ifEmpty { "0" },
                            xmlField(b1, "wifibandwidth"), xmlField(b1, "WifiMode"))
                    }
                    advReady = true
                } else {
                    advBox.addView(noteView(act, "Pengaturan lanjutan (channel/bandwidth) tidak tersedia dari router ini."))
                }
            }
            r.onFailure { advBox.addView(noteView(act, "Pengaturan lanjutan gagal dimuat: ${it.message}")) }
        }
    }

    /** One band's advanced group; rendered only when the router actually has that band. */
    private fun buildBandSection(is5g: Boolean, curChan0: String, curBw0: String, curMode0: String) {
        val head = Ui.text(act, if (is5g) "LANJUTAN · 5 GHz" else "LANJUTAN · 2.4 GHz", 13f, Ui.TXT, true)
        head.setPadding(0, Ui.dp(act, 18), 0, 0)
        advBox.addView(head)
        advBox.addView(fldLabel(act, "CHANNEL"))
        val curChan = curChan0.ifEmpty { "0" }
        var chanVals = (if (is5g) CH5_BASE else CH24_BASE).toMutableList()
        if (curChan !in chanVals) chanVals.add(0, curChan)
        val chanLabels = chanVals.map { if (it == "0") "Otomatis" else it }
        val grid = ChipGrid(act, chanLabels, if (is5g) 5 else 7) {}
        grid.select(chanVals.indexOf(curChan).coerceAtLeast(0))
        advBox.addView(grid)
        advBox.addView(fldLabel(act, "BANDWIDTH"))
        val bwRow = ChipRow(act, listOf("Otomatis", "20 MHz", "40 MHz")) {}
        val curBw = curBw0
        bwRow.select(BW_VALS.indexOf(curBw).takeIf { it >= 0 } ?: 0)
        advBox.addView(bwRow)
        advBox.addView(fldLabel(act, "MODE"))
        val curMode = curMode0
        var modeVals = (if (is5g) listOf("a/n/ac", "a/n", "a") else listOf("b/g/n")).toMutableList()
        if (curMode.isNotEmpty() && curMode !in modeVals) modeVals.add(0, curMode)
        val modeRow = ChipRow(act, modeVals.map { "802.11$it" }) {}
        modeRow.select(modeVals.indexOf(curMode).coerceAtLeast(0))
        advBox.addView(modeRow)
        if (is5g) {
            gridChan5 = grid; bwRow5 = bwRow; modeRow5 = modeRow
            chanVals5 = chanVals; modeVals5 = modeVals
        } else {
            gridChan24 = grid; bwRow24 = bwRow; modeRow24 = modeRow
            chanVals24 = chanVals; modeVals24 = modeVals
        }
    }

    /** Full basic-settings body (WebUI-style read-modify-write): the router's own
     * primary record with the basic + advanced fields patched; every other byte
     * goes back exactly as read. This is the record the radio actually follows. */
    private fun buildBasicBody(c: HilinkClient, ssid: String, adv: List<String>,
                               isolate: String, hide: Boolean, enable: String): String {
        val raw = c.wlanBasicRaw()
        val start = raw.indexOf("<response>")
        if (start < 0) throw IllegalStateException("basic-settings tidak terbaca")
        var body = raw.substring(start)
            .replaceFirst("<response>", "<request>")
            .replace("</response>", "</request>")
        val end = body.indexOf("</request>")
        if (end >= 0) body = body.substring(0, end + "</request>".length)
        body = patchXmlField(body, "WifiSsid", ssid)
        val curHide = xmlField(body, "WifiHide")
        body = patchXmlField(body, "WifiHide",
            if (curHide == "0" || curHide == "1") (if (hide) "1" else "0") else hide.toString())
        if (enable.isNotEmpty()) body = patchXmlField(body, "WifiEnable", enable)
        if (adv[0].isNotEmpty()) body = patchXmlField(body, "WifiChannel", adv[0])
        if (adv[1].isNotEmpty()) body = patchXmlField(body, "wifibandwidth", adv[1])
        if (adv[2].isNotEmpty()) body = patchXmlField(body, "WifiMode", adv[2])
        body = patchXmlField(body, "WifiIsolate", isolate)
        body = if (body.contains("<WifiRestart>")) patchXmlField(body, "WifiRestart", "1")
            else body.replace("</request>", "<WifiRestart>1</WifiRestart></request>")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + body
    }

    /** Full multi-basic body: the router's own profile blocks with only the advanced
     *  fields (and the shared isolation switch) patched — everything else byte-identical. */
    private fun buildAdvancedBody(): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Ssids>")
        advBlocks.forEachIndexed { i, orig ->
            var b = orig
            if (advIs5g[i]) {
                gridChan5?.let { b = patchXmlField(b, "WifiChannel", chanVals5[it.sel]) }
                bwRow5?.let { b = patchXmlField(b, "wifibandwidth", BW_VALS[it.sel]) }
                modeRow5?.let { b = patchXmlField(b, "WifiMode", modeVals5[it.sel]) }
            } else {
                gridChan24?.let { b = patchXmlField(b, "WifiChannel", chanVals24[it.sel]) }
                bwRow24?.let { b = patchXmlField(b, "wifibandwidth", BW_VALS[it.sel]) }
                modeRow24?.let { b = patchXmlField(b, "WifiMode", modeVals24[it.sel]) }
            }
            b = patchXmlField(b, "WifiIsolate", if (isolateSw.isChecked) "1" else "0")
            if (b.contains("<WifiRestart>")) b = patchXmlField(b, "WifiRestart", "1")
            sb.append(b)
        }
        sb.append("</Ssids></request>")
        return sb.toString()
    }

    private fun load() {
        status.text = "Memuat pengaturan WiFi…"
        Bg.run({ Pair(act.app().client().wlanBasic(), act.app().client().wlanSecurity()) }) { r ->
            r.onSuccess { (basic, sec) ->
                ssidIn.setText(HilinkClient.ci(basic, "WifiSsid").ifEmpty { HilinkClient.ci(basic, "SsId") })
                val hide = HilinkClient.ci(basic, "WifiHide")
                hideSw.isChecked = hide == "1" || hide.equals("true", true)
                enableKnown = HilinkClient.ci(basic, "WifiEnable")
                enableSw.isChecked = enableKnown != "0"
                origAuth = HilinkClient.ci(sec, "WifiAuthmode")
                authRow.select(when {
                    origAuth.contains("WPA2") && !origAuth.contains("WPA/") -> 0
                    origAuth.contains("WPA") -> 1
                    origAuth.equals("OPEN", true) || origAuth == "0" -> 2
                    else -> 0
                })
                val pk = HilinkClient.ci(sec, "WifiWpapsk")
                if (pk.isNotEmpty() && pk != "****") pskIn.setText(pk)
                else pskIn.hint = "Tersimpan di router (isi untuk mengganti)"
                status.text = ""
            }
            r.onFailure { status.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun confirmSave() {
        val ssid = ssidIn.text.toString().trim()
        if (ssid.isEmpty()) { Ui.toast(act, "Nama WiFi tidak boleh kosong"); return }
        if (authRow.sel < 2 && pskIn.text.isNotEmpty() && pskIn.text.length < 8) {
            Ui.toast(act, "Password WiFi minimal 8 karakter"); return
        }
        Ui.confirm(act, "Terapkan pengaturan WiFi?",
            "Router akan me-restart WiFi-nya. HP kamu terputus sebentar — sambungkan ulang ke \"$ssid\" setelahnya.",
            "Terapkan") { apply(ssid) }
    }

    private fun apply(ssid: String) {
        val authValue = listOf("WPA2-PSK", "WPA/WPA2-PSK", "OPEN")[authRow.sel]
        val pskText = pskIn.text.toString()
        val needSecurity = authValue != origAuth || pskText.isNotEmpty()
        val pskToSend: String? = when {
            authRow.sel == 2 -> ""
            pskText.isNotEmpty() -> pskText
            else -> null
        }
        val advBody = if (advReady && advBlocks.isNotEmpty()) buildAdvancedBody() else null
        // Capture the basic band's advanced picks + switch states on the UI thread.
        val advBasicVals: List<String>? = if (basicAdvReady) {
            if (basicIs5g) listOf(
                gridChan5?.let { chanVals5[it.sel] } ?: "",
                bwRow5?.let { BW_VALS[it.sel] } ?: "",
                modeRow5?.let { modeVals5[it.sel] } ?: "")
            else listOf(
                gridChan24?.let { chanVals24[it.sel] } ?: "",
                bwRow24?.let { BW_VALS[it.sel] } ?: "",
                modeRow24?.let { modeVals24[it.sel] } ?: "")
        } else null
        val isolateVal = if (isolateSw.isChecked) "1" else "0"
        val hideVal = hideSw.isChecked
        val enableVal = if (enableKnown.isNotEmpty()) (if (enableSw.isChecked) "1" else "0") else ""
        status.text = "Menerapkan…"
        Bg.run({
            val c = act.app().client()
            val errs = mutableListOf<String>()
            var basicOk = false
            var advBasicOk = !basicAdvReady
            try {
                if (needSecurity) c.setWlanSecurity(authValue, pskToSend)
                if (basicAdvReady && advBasicVals != null) {
                    try {
                        c.setWlanBasicRaw(buildBasicBody(c, ssid, advBasicVals, isolateVal, hideVal, enableVal))
                        basicOk = true; advBasicOk = true
                    } catch (e: Exception) {
                        errs.add("lanjutan (basic-settings): ${e.message}")
                        c.setWlanBasic(ssid, hideVal, enableVal)
                        basicOk = true
                    }
                } else {
                    c.setWlanBasic(ssid, hideVal, enableVal)
                    basicOk = true
                }
            } catch (e: Exception) {
                errs.add("dasar: ${e.message}")
            }
            var multiOk = advBody == null
            if (advBody != null) {
                try {
                    c.setWlanMultiBasic(advBody)
                    multiOk = true
                } catch (e: Exception) {
                    errs.add("sinkron multi-profil: ${e.message}")
                }
            }
            Pair(listOf(basicOk, advBasicOk, multiOk), errs)
        }) { r ->
            r.onSuccess { (flags, errs) ->
                val (basicOk, advBasicOk, multiOk) = flags
                status.text = when {
                    errs.isEmpty() && basicOk && advBasicOk && multiOk ->
                        "✓ Diterapkan. Bila WiFi putus, sambungkan ulang lalu kembali ke app."
                    basicOk || multiOk ->
                        "Sebagian diterapkan — gagal: ${errs.joinToString("; ")}"
                    else -> "Gagal: ${errs.joinToString()}"
                }
            }
            r.onFailure { status.text = "Gagal: ${it.message}" }
        }
    }
}

// ---------------- DHCP ----------------

class DhcpScreen(private val act: MainActivity) : Screen {
    private lateinit var status: TextView
    private lateinit var gwIn: EditText
    private lateinit var maskIn: EditText
    private lateinit var startIn: EditText
    private lateinit var endIn: EditText
    private lateinit var dns1In: EditText
    private lateinit var dns2In: EditText
    private lateinit var dhcpSw: Switch
    private lateinit var leaseRow: ChipRow
    private val leaseVals = listOf(43200, 86400, 172800, 604800)

    override fun build(actA: Activity): View {
        val ctx = act
        val (scroll, root) = subPage(act, "LAN & DHCP", "Alamat router & pembagian IP otomatis")
        status = Ui.text(ctx, "", 13f, Ui.SUB); root.addView(status)
        root.addView(fldLabel(act, "IP ROUTER (GATEWAY)"))
        gwIn = fld(act, "192.168.8.1"); root.addView(gwIn)
        root.addView(fldLabel(act, "SUBNET MASK"))
        maskIn = fld(act, "255.255.255.0"); root.addView(maskIn)
        root.addView(gapView(act, 6))
        val card = Ui.card(ctx)
        dhcpSw = switchRow(act, card, "Server DHCP aktif")
        root.addView(card)
        root.addView(fldLabel(act, "RENTANG IP UNTUK PERANGKAT"))
        val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
        startIn = fld(act, "Mulai"); endIn = fld(act, "Sampai")
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp2.leftMargin = Ui.dp(ctx, 10)
        row.addView(startIn, lp1); row.addView(endIn, lp2)
        root.addView(row)
        root.addView(fldLabel(act, "LAMA SEWA (LEASE)"))
        leaseRow = ChipRow(act, listOf("12 jam", "24 jam", "48 jam", "1 minggu")) {}
        leaseRow.select(1)
        root.addView(leaseRow)
        root.addView(fldLabel(act, "DNS PRIMER"))
        dns1In = fld(act, "mis. 1.1.1.1"); root.addView(dns1In)
        root.addView(fldLabel(act, "DNS SEKUNDER"))
        dns2In = fld(act, "mis. 1.0.0.1 (opsional)"); root.addView(dns2In)
        root.addView(noteView(act, "Kedua kolom DNS kosong = DNS otomatis dari operator."))
        root.addView(noteView(act, "Mengubah IP router akan memutus app dari alamat lama — app otomatis menyimpan & mengikuti alamat baru. Pastikan HP tetap terhubung ke WiFi router ini."))
        root.addView(gapView(act, 14))
        val save = Ui.button(ctx, "SIMPAN & TERAPKAN")
        save.setOnClickListener { confirmSave() }
        root.addView(save)
        load()
        return scroll
    }

    private fun load() {
        status.text = "Memuat pengaturan DHCP…"
        Bg.run({ act.app().client().dhcpSettings() }) { r ->
            r.onSuccess { m ->
                gwIn.setText(HilinkClient.ci(m, "DhcpIPAddress"))
                // firmware keys proven from the WebUI's dhcp.js: the mask is
                // DhcpLanNetmask and the server on/off is DhcpStatus (1/0) —
                // the generic names (DhcpSubnetMask/DhcpEnable) do not exist.
                maskIn.setText(HilinkClient.ci(m, "DhcpLanNetmask").ifEmpty { HilinkClient.ci(m, "DhcpSubnetMask") })
                startIn.setText(HilinkClient.ci(m, "DhcpStartIPAddress"))
                endIn.setText(HilinkClient.ci(m, "DhcpEndIPAddress"))
                dhcpSw.isChecked = HilinkClient.ci(m, "DhcpStatus").ifEmpty { HilinkClient.ci(m, "DhcpEnable") } != "0"
                dns1In.setText(HilinkClient.ci(m, "PrimaryDns"))
                dns2In.setText(HilinkClient.ci(m, "SecondaryDns"))
                val lease = HilinkClient.ci(m, "DhcpLeaseTime").toIntOrNull() ?: 86400
                leaseRow.select(leaseVals.indices.minByOrNull { kotlin.math.abs(leaseVals[it] - lease) } ?: 1)
                status.text = ""
            }
            r.onFailure { status.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun validIp(s: String): Boolean {
        val parts = s.split(".")
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun confirmSave() {
        val gw = gwIn.text.toString().trim()
        val start = startIn.text.toString().trim()
        val end = endIn.text.toString().trim()
        val mask = maskIn.text.toString().trim()
        val dns1 = dns1In.text.toString().trim()
        val dns2 = dns2In.text.toString().trim()
        if (!validIp(gw) || !validIp(mask) || !validIp(start) || !validIp(end) ||
            (dns1.isNotEmpty() && !validIp(dns1)) || (dns2.isNotEmpty() && !validIp(dns2))
        ) {
            Ui.toast(act, "Format IP belum benar"); return
        }
        // the app will follow a gateway change — it must stay a LAN address
        val guard = LanGuard.check(gw)
        if (guard is LanGuard.Result.Bad) {
            Ui.toast(act, guard.reason); return
        }
        val changed = gw != act.app().prefs.routerIp
        Ui.confirm(act, "Terapkan pengaturan DHCP?",
            if (changed) "IP router berubah dari ${act.app().prefs.routerIp} ke $gw. App akan mengikuti alamat baru; koneksi ke alamat lama putus."
            else "Pengaturan DHCP akan ditulis ke router.",
            "Terapkan") { apply(gw, start, end, changed) }
    }

    private fun apply(gw: String, start: String, end: String, gwChanged: Boolean) {
        status.text = "Menerapkan…"
        val dns1 = dns1In.text.toString().trim()
        val dns2 = dns2In.text.toString().trim()
        val overrides = mapOf(
            "DhcpIPAddress" to gw,
            "DhcpLanNetmask" to maskIn.text.toString().trim(),
            "DhcpStatus" to if (dhcpSw.isChecked) "1" else "0",
            "DhcpStartIPAddress" to start,
            "DhcpEndIPAddress" to end,
            "DhcpLeaseTime" to leaseVals[leaseRow.sel].toString(),
            // WebUI semantics (dhcp.js): any DNS filled → manual (DnsStatus 0);
            // both empty → automatic from the operator (DnsStatus 1).
            "PrimaryDns" to dns1,
            "SecondaryDns" to dns2,
            "DnsStatus" to if (dns1.isNotEmpty() || dns2.isNotEmpty()) "0" else "1",
        )
        Bg.run({ act.app().client().setDhcp(overrides); true }) { r ->
            r.onSuccess {
                if (gwChanged) {
                    act.app().prefs.routerIp = gw
                    act.app().resetClient()
                    status.text = "✓ IP router diganti ke $gw — app sekarang menunjuk ke alamat baru."
                    Ui.toast(act, "IP router diganti ke $gw")
                } else status.text = "✓ Diterapkan."
            }
            r.onFailure { status.text = "Gagal: ${it.message}" }
        }
    }
}

// ---------------- USSD ----------------

class UssdScreen(private val act: MainActivity) : Screen {
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private lateinit var codeIn: EditText
    private lateinit var sendBtn: TextView

    override fun build(actA: Activity): View {
        val ctx = act
        val (scroll, root) = subPage(act, "USSD", "Kode dial operator lewat SIM di router")
        status = Ui.text(ctx, "", 13f, Ui.SUB); root.addView(status)
        val card = Ui.card(ctx)
        reply = Ui.text(ctx, "Balasan operator tampil di sini.\n\nContoh: kirim *123# untuk menu Tri — cek kuota & pulsa tanpa memindahkan SIM ke HP.", 15.5f, Ui.TXT)
        reply.setTextIsSelectable(true)
        card.addView(reply)
        root.addView(card)
        root.addView(fldLabel(act, "KODE USSD"))
        codeIn = fld(act, "*123#"); codeIn.setText("*123#")
        root.addView(codeIn)
        val chips = LinearLayout(ctx); chips.orientation = LinearLayout.HORIZONTAL
        listOf("*123#", "*111#", "*123*10#", "*888#").forEachIndexed { i, n ->
            val c = Ui.chip(ctx, n, false)
            c.setOnClickListener { codeIn.setText(n) }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(ctx, 8)
            chips.addView(c, lp)
        }
        root.addView(chips)
        root.addView(gapView(act, 14))
        sendBtn = Ui.button(ctx, "KIRIM")
        sendBtn.setOnClickListener { send() }
        root.addView(sendBtn)
        return scroll
    }

    private fun send() {
        val code = codeIn.text.toString().trim()
        if (code.isEmpty()) { Ui.toast(act, "Isi kode USSD dulu"); return }
        sendBtn.isEnabled = false
        status.text = "Mengirim $code — menunggu balasan operator (bisa sampai ±2 menit)…"
        Bg.run({
            val c = act.app().client()
            // Protokol WebUI router (ussd.js): tutup dulu dialog USSD yang mungkin
            // menggantung dari percobaan lama, baru kirim SEKALI dengan bentuk baku.
            c.ussdRelease()
            Thread.sleep(400)
            val statusBefore = try { HilinkClient.ci(c.ussdStatus(), "result") } catch (e: Exception) { "" }
            c.ussdSend(code)
            // Polling sabar: error 111019 dari ussd/get artinya operator BELUM
            // menjawab — WebUI melanjutkan polling sampai timer 2 menit, bukan gagal.
            var content = ""; var resultType = ""
            var lastErr = ""
            val deadline = System.currentTimeMillis() + 110_000
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(2000)
                try {
                    val m = c.ussdGet()
                    content = UssdText.decode(HilinkClient.ci(m, "content"))
                    resultType = HilinkClient.ci(m, "resulttype")
                    if (content.isNotBlank()) break
                } catch (e: HilinkException) {
                    lastErr = e.code
                    if (e.code == "111020") break // network timeout — final, berhenti menunggu
                    // 111019 & error polling lain: terus menunggu
                }
            }
            // Tutup dialog bila sesi selesai/gagal; biarkan terbuka bila operator
            // menunggu balasan menu (resulttype != "1" dan ada isi).
            val sessionOpen = content.isNotBlank() && resultType.isNotEmpty() && resultType != "1"
            if (!sessionOpen) c.ussdRelease()
            Triple(content, resultType, lastErr)
        }) { r ->
            sendBtn.isEnabled = true
            r.onSuccess { (content, resultType, lastErr) ->
                if (content.isNotBlank()) {
                    reply.text = content
                    status.text = if (resultType.isNotEmpty() && resultType != "1")
                        "Sesi masih terbuka — ketik balasan (angka menu) lalu KIRIM lagi."
                    else "Sesi selesai."
                } else status.text = "Operator tidak menjawab setelah ±2 menit menunggu" +
                    (if (lastErr.isNotEmpty()) " (kode terakhir: $lastErr)" else "") +
                    ". Dialog USSD sudah ditutup ulang — silakan coba lagi."
            }
            r.onFailure { status.text = "Gagal: ${it.message}" }
        }
    }
}

// ---------------- APN profiles ----------------

class ApnScreen(private val act: MainActivity) : Screen {
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var nameIn: EditText
    private lateinit var apnIn: EditText
    private lateinit var userIn: EditText
    private lateinit var passIn: EditText
    private lateinit var delBtn: TextView
    private lateinit var activeBtn: TextView
    private lateinit var authRow: ChipRow
    private var selectedIndex = ""
    private var currentIndex = ""

    override fun build(actA: Activity): View {
        val ctx = act
        val (scroll, root) = subPage(act, "Profil APN", "Profil koneksi data yang tersimpan di router")
        status = Ui.text(ctx, "", 13f, Ui.SUB); root.addView(status)
        listBox = LinearLayout(ctx); listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox)
        val newBtn = Ui.button(ctx, "+ PROFIL BARU", false)
        newBtn.setOnClickListener { clearForm() }
        root.addView(newBtn)
        root.addView(fldLabel(act, "NAMA PROFIL"))
        nameIn = fld(act, "Nama profil"); root.addView(nameIn)
        root.addView(fldLabel(act, "APN"))
        apnIn = fld(act, "mis. internet / 3gprs"); root.addView(apnIn)
        root.addView(fldLabel(act, "USERNAME"))
        userIn = fld(act, "Boleh kosong"); root.addView(userIn)
        root.addView(fldLabel(act, "PASSWORD"))
        val (apnPassBox, apnPassEdit) = Ui.passwordField(act, "Boleh kosong")
        passIn = apnPassEdit
        root.addView(apnPassBox)
        root.addView(fldLabel(act, "AUTENTIKASI"))
        authRow = ChipRow(act, listOf("Otomatis", "PAP", "CHAP")) {}
        root.addView(authRow)
        root.addView(gapView(act, 14))
        activeBtn = Ui.button(ctx, "JADIKAN AKTIF")
        activeBtn.setOnClickListener { makeActive() }
        root.addView(activeBtn)
        root.addView(gapView(act, 10))
        val saveBtn = Ui.button(ctx, "SIMPAN PROFIL")
        saveBtn.setOnClickListener { save() }
        root.addView(saveBtn)
        root.addView(gapView(act, 10))
        delBtn = Ui.button(ctx, "HAPUS PROFIL INI", false)
        delBtn.setOnClickListener { confirmDelete() }
        root.addView(delBtn)
        root.addView(noteView(act, "Menjadikan profil aktif akan menyambung ulang koneksi data. Sebagian firmware menolak penulisan profil lewat API — bila ditolak, pesan error router akan tampil apa adanya."))
        clearForm()
        load()
        return scroll
    }

    private fun clearForm() {
        selectedIndex = ""
        nameIn.setText(""); apnIn.setText(""); userIn.setText(""); passIn.setText("")
        authRow.select(0)
        activeBtn.visibility = View.GONE; delBtn.visibility = View.GONE
    }

    private fun fillForm(p: HilinkClient.ApnProfile) {
        selectedIndex = p.index
        nameIn.setText(p.name); apnIn.setText(p.apn); userIn.setText(p.username)
        passIn.setText(if (p.password == "****") "" else p.password)
        if (p.password == "****") passIn.hint = "Tersimpan (isi untuk mengganti)"
        authRow.select(p.authMode.toIntOrNull() ?: 0)
        activeBtn.visibility = if (p.index == currentIndex) View.GONE else View.VISIBLE
        delBtn.visibility = if (p.index == currentIndex) View.GONE else View.VISIBLE
    }

    private fun load() {
        status.text = "Memuat profil…"
        Bg.run({ act.app().client().apnProfiles() }) { r ->
            r.onSuccess { (list, current) ->
                currentIndex = current
                listBox.removeAllViews()
                if (list.isEmpty()) {
                    val t = Ui.text(act, "Tidak ada profil terbaca dari router.", 14f, Ui.SUB)
                    listBox.addView(t)
                }
                list.forEach { p -> listBox.addView(profileRow(p, p.index == current) { fillForm(p) }) }
                status.text = ""
            }
            r.onFailure { status.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun profileRow(p: HilinkClient.ApnProfile, active: Boolean, onClick: () -> Unit): View {
        val ctx = act
        val card = Ui.card(ctx)
        val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(ctx, p.name, 16f, Ui.TXT, true))
        col.addView(Ui.text(ctx, "APN: ${p.apn}" + (if (p.username.isNotEmpty()) " · user: ${p.username}" else " · tanpa user"), 12.5f, Ui.SUB))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (active) row.addView(Ui.text(ctx, "AKTIF", 11.5f, Ui.GREEN, true))
        card.addView(row)
        card.setOnClickListener { onClick() }
        return card
    }

    private fun save() {
        val name = nameIn.text.toString().trim()
        if (name.isEmpty()) { Ui.toast(act, "Nama profil tidak boleh kosong"); return }
        val p = HilinkClient.ApnProfile(selectedIndex, name, apnIn.text.toString().trim(),
            userIn.text.toString().trim(), passIn.text.toString(), authRow.sel.toString(), "0")
        status.text = "Menyimpan…"
        Bg.run({ act.app().client().saveProfile(p, selectedIndex.isEmpty()); true }) { r ->
            r.onSuccess { status.text = "✓ Profil tersimpan."; clearForm(); load() }
            r.onFailure { status.text = "Gagal: ${it.message}" }
        }
    }

    private fun makeActive() {
        if (selectedIndex.isEmpty()) return
        status.text = "Mengaktifkan profil…"
        Bg.run({ act.app().client().setDefaultProfile(selectedIndex); true }) { r ->
            r.onSuccess { status.text = "✓ Profil aktif diganti — koneksi data menyambung ulang."; load() }
            r.onFailure { status.text = "Gagal: ${it.message}" }
        }
    }

    private fun confirmDelete() {
        if (selectedIndex.isEmpty()) return
        Ui.confirm(act, "Hapus profil ini?", "Profil \"${nameIn.text}\" akan dihapus dari router.", "Hapus") {
            status.text = "Menghapus…"
            Bg.run({ act.app().client().deleteProfile(selectedIndex); true }) { r ->
                r.onSuccess { status.text = "✓ Profil dihapus."; clearForm(); load() }
                r.onFailure { status.text = "Gagal: ${it.message}" }
            }
        }
    }
}

// ---------------- Diagnostik ----------------

/**
 * DNS-style diagnostics: one run checks every endpoint, shows a live checklist,
 * and assembles a single JSON report (meta + snapshot + checks + scrubbed raw payloads)
 * that can be copied, shared, or saved to Download — like NetAver DNS's Debug Capture.
 */
class DiagScreen(private val act: MainActivity) : Screen {
    private lateinit var status: TextView
    private lateinit var lines: TextView
    private var reportJson = ""
    private var reportName = ""

    private fun ui(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
    }

    override fun build(actA: Activity): View {
        val ctx = act
        val (scroll, root) = subPage(act, "Diagnostik", "Laporan lengkap ala Debug Capture NetAver DNS")
        root.addView(Ui.text(ctx,
            "Satu ketukan menjalankan pemeriksaan semua endpoint router (sinyal, status, trafik, SMS, APN, USSD) lalu menyusunnya jadi satu laporan JSON — siap disalin, dibagikan, atau disimpan ke folder Download. Nomor seri/IMEI/MAC disamarkan otomatis; kata sandi router tidak pernah masuk laporan.",
            13f, Ui.SUB))
        root.addView(gapView(act, 10))
        status = Ui.text(ctx, "", 13f, Ui.SUB); root.addView(status)
        val run = Ui.button(ctx, "JALANKAN DIAGNOSTIK")
        run.setOnClickListener { runDiag() }
        root.addView(run)
        root.addView(gapView(act, 10))
        val row1 = LinearLayout(ctx); row1.orientation = LinearLayout.HORIZONTAL
        val bCopy = Ui.button(ctx, "SALIN", false)
        bCopy.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bCopy.setOnClickListener {
            if (reportJson.isEmpty()) { Ui.toast(act, "Jalankan diagnostik dulu"); return@setOnClickListener }
            val cm = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostik HiLink", reportJson))
            Ui.toast(act, "Laporan disalin — tempel & kirim ke chat")
        }
        row1.addView(bCopy)
        val bShare = Ui.button(ctx, "BAGIKAN", false)
        bShare.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bShare.setOnClickListener {
            if (reportJson.isEmpty()) { Ui.toast(act, "Jalankan diagnostik dulu"); return@setOnClickListener }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, reportName)
            intent.putExtra(android.content.Intent.EXTRA_TEXT, reportJson)
            act.startActivity(android.content.Intent.createChooser(intent, "Bagikan laporan diagnostik"))
        }
        row1.addView(bShare)
        root.addView(row1)
        root.addView(gapView(act, 8))
        val bSave = Ui.button(ctx, "SIMPAN KE DOWNLOAD", false)
        bSave.setOnClickListener { saveReport() }
        root.addView(bSave)
        root.addView(gapView(act, 12))
        val card = Ui.card(ctx)
        lines = Ui.text(ctx, "Hasil pemeriksaan tampil di sini.", 12.5f, Ui.TXT)
        lines.setTextIsSelectable(true)
        card.addView(lines)
        root.addView(card)
        return scroll
    }

    private fun saveReport() {
        if (reportJson.isEmpty()) { Ui.toast(act, "Jalankan diagnostik dulu"); return }
        try {
            val cv = android.content.ContentValues()
            cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, reportName)
            cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
            val uri = act.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("insert gagal")
            act.contentResolver.openOutputStream(uri)?.use { it.write(reportJson.toByteArray(Charsets.UTF_8)) }
            Ui.toast(act, "Tersimpan di Download/$reportName")
        } catch (e: Exception) {
            Ui.toast(act, "Gagal menyimpan: ${e.message}")
        }
    }

    private fun runDiag() {
        status.text = "Memeriksa router…"
        lines.text = ""
        reportJson = ""
        val c = act.app().client()
        val prefs = act.app().prefs
        Bg.run({
            val checks = org.json.JSONArray()
            val snap = org.json.JSONObject()
            var pendingVariants = org.json.JSONArray()
            var pendingProbe = org.json.JSONArray()
            fun line(txt: String) { ui { lines.text = lines.text.toString() + txt + "\n" } }
            fun check(name: String, block: () -> String): String {
                val t0 = System.currentTimeMillis()
                return try {
                    val detail = block()
                    val ms = System.currentTimeMillis() - t0
                    checks.put(org.json.JSONObject().put("name", name).put("ok", true).put("ms", ms).put("detail", detail))
                    line("✓ $name — $detail (${ms} ms)")
                    detail
                } catch (e: Exception) {
                    val ms = System.currentTimeMillis() - t0
                    val msg = e.message ?: e.javaClass.simpleName
                    checks.put(org.json.JSONObject().put("name", name).put("ok", false).put("ms", ms).put("detail", msg))
                    line("✗ $name — $msg (${ms} ms)")
                    ""
                }
            }
            check("Jangkauan router") {
                val r = c.testConnection()
                if (r.ok) "terjangkau (${r.note})" else throw IllegalStateException(r.note)
            }
            check("Sesi login") {
                c.ensureSession()
                if (c.loggedIn) "aktif" else throw IllegalStateException("belum login")
            }
            check("Informasi perangkat") {
                val d = c.deviceInfo()
                snap.put("device", org.json.JSONObject()
                    .put("name", d.name).put("hardware", d.hw).put("software", d.sw))
                listOf(d.name, d.sw).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "terbaca" }
            }
            var bandShown = ""
            check("Sinyal") {
                val sg = c.signal()
                snap.put("signal", org.json.JSONObject()
                    .put("rsrp", sg.rsrp).put("rsrq", sg.rsrq).put("sinr", sg.sinr).put("rssi", sg.rssi)
                    .put("pci", sg.pci).put("cell_id", sg.cellId).put("band", sg.band))
                bandShown = sg.band
                "RSRP ${sg.rsrp} · RSRQ ${sg.rsrq} · SINR ${sg.sinr} · PCI ${sg.pci} · Cell ${sg.cellId}" +
                    if (sg.band.isNotEmpty()) " · Band B${sg.band}" else " · Band: TIDAK DITEMUKAN"
            }
            check("Status koneksi") {
                val st = c.status()
                snap.put("status", org.json.JSONObject()
                    .put("connection", st.connectionStatus).put("network_type", st.networkType)
                    .put("sim", st.simStatus).put("battery", st.batteryLevel))
                "koneksi ${st.connectionStatus} · tipe ${st.networkType} · SIM ${st.simStatus} · baterai ${st.batteryLevel}"
            }
            check("Trafik") {
                val t = c.traffic()
                snap.put("traffic", org.json.JSONObject()
                    .put("session_down", t.curDown).put("session_up", t.curUp)
                    .put("total_down", t.totalDown).put("total_up", t.totalUp).put("connect_sec", t.connectTimeSec))
                "sesi ↓${com.netaver.hilink.core.Format.bytes(t.curDown)} ↑${com.netaver.hilink.core.Format.bytes(t.curUp)}"
            }
            check("Mode jaringan") {
                val nm = c.netMode()
                snap.put("net_mode", org.json.JSONObject()
                    .put("mode", nm.mode).put("network_band", nm.networkBand).put("lte_band_mask", nm.lteBand))
                "mode ${nm.mode} · masker LTE ${nm.lteBand}"
            }
            check("SMS belum dibaca") { "${c.unreadSms()} pesan" }
            check("Perangkat terhubung") { "${c.hosts().size} perangkat" }
            check("Profil APN") {
                val (list, current) = c.apnProfiles()
                "${list.size} profil · aktif: $current"
            }
            check("Profil WLAN (basic + multi)") {
                val b = c.wlanBasic()
                val head = "utama (basic): ${HilinkClient.ci(b, "WifiSsid")} · mode ${HilinkClient.ci(b, "WifiMode")} · ch ${HilinkClient.ci(b, "WifiChannel")} · bw ${HilinkClient.ci(b, "wifibandwidth")} · isolasi ${HilinkClient.ci(b, "WifiIsolate")} · negara ${HilinkClient.ci(b, "WifiCountry")}"
                val profs = HilinkClient.blocks(c.wlanMultiBasicRaw(), "Ssid")
                if (profs.isEmpty()) {
                    head + " || multi-basic kosong"
                } else {
                    head + " || " + profs.joinToString(" | ") { blk ->
                        val m = HilinkClient.parseXml(blk)
                        "${HilinkClient.ci(m, "WifiSsid")} · mode ${HilinkClient.ci(m, "WifiMode")} · ch ${HilinkClient.ci(m, "WifiChannel")} · bw ${HilinkClient.ci(m, "wifibandwidth")} · isolasi ${HilinkClient.ci(m, "WifiIsolate")} · negara ${HilinkClient.ci(m, "WifiCountry")}"
                    }
                }
            }
            check("Status USSD") {
                val m = c.ussdStatus()
                val st = HilinkClient.ci(m, "result")
                if (st.isNotEmpty()) "result modem: $st" else "terbaca (${m.size} kolom)"
            }
            check("add_param.xml (band jalur mod)") {
                val ap = c.addParam()
                val b = HilinkClient.ci(ap, "band")
                val e = HilinkClient.ci(ap, "earfcn1")
                if (b.isEmpty() && e.isEmpty()) throw IllegalStateException("file ada tapi band kosong")
                "band=$b · earfcn=$e · freq=${HilinkClient.ci(ap, "freq1")} · lac=${HilinkClient.ci(ap, "lac")}"
            }
            val variants = org.json.JSONArray()
            check("Varian sinyal (perburuan band)") {
                var found = ""
                val verdicts = ArrayList<String>()
                for ((name, raw) in c.signalVariants()) {
                    variants.put(org.json.JSONObject().put("name", name).put("raw", raw))
                    val b = com.netaver.hilink.core.Format.bandFromRawXml(raw)
                    val rich = raw.contains("earfcn", true) || raw.contains("txpower", true)
                    verdicts.add("$name: " + when {
                        b != null -> "BAND B$b KETEMU"
                        rich -> "ada earfcn/txpower"
                        raw.startsWith("ERROR") -> raw.lineSequence().firstOrNull() ?: "error"
                        else -> "sama (minim)"
                    })
                    if (b != null && found.isEmpty()) found = "B$b via $name"
                }
                if (found.isEmpty()) verdicts.joinToString(" | ") else "$found || " + verdicts.joinToString(" | ")
            }
            pendingVariants = variants
            val probeArr = org.json.JSONArray()
            check("Bedah JS WebUI (jalur band/USSD mod)") {
                val probe = c.webUiProbe()
                for ((n, v2) in probe) probeArr.put(org.json.JSONObject().put("name", n).put("content", v2))
                val apis = probe.firstOrNull { it.first.startsWith("endpoint api unik") }?.second ?: ""
                apis.take(220).ifEmpty { "tidak ada endpoint ditemukan" }
            }
            pendingProbe = probeArr
            val rawDump = check("Data mentah endpoint") {
                val d = c.rawDump()
                "terkumpul ${d.length} karakter"
                d
            }
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            val now = java.util.Date()
            val root = org.json.JSONObject()
            root.put("app", "NetAver HiLink")
            root.put("generated", fmt.format(now))
            root.put("app_version", "2.1.8 (15)")
            root.put("android", "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            root.put("phone", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            root.put("router_address", prefs.routerIp)
            root.put("band_resolved", bandShown)
            root.put("checks", checks)
            root.put("snapshot", snap)
            root.put("raw_dump", rawDump)
            root.put("signal_variants", pendingVariants)
            root.put("webui_probe", pendingProbe)
            reportName = "netaver-hilink-diagnostik-" + stamp.format(now) + ".json"
            root.toString(2)
        }) { r ->
            r.onSuccess {
                reportJson = it
                status.text = "✓ Selesai — laporan siap: SALIN / BAGIKAN / SIMPAN."
            }
            r.onFailure { status.text = "Gagal menyusun laporan: ${it.message}" }
        }
    }
}

package com.netaver.hilink.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Arah Antena: live signal compass mirroring the mod WebUI's antennapointing page.
 * Fast tick (1 s) reads the raw signal map; slow tick (5 s) refreshes cell/frequency
 * data from add_param.xml + PLMN + status. Tracks session peaks for RSRP and SINR.
 */
class PointingScreen(private val act: MainActivity) : Screen {

    private lateinit var ratTv: TextView
    private lateinit var rssiTv: TextView
    private lateinit var lab1: TextView
    private lateinit var val1: TextView
    private lateinit var lab2: TextView
    private lateinit var val2: TextView
    private lateinit var lab3: TextView
    private lateinit var val3: TextView
    private lateinit var peakTv: TextView
    private val grid = mutableMapOf<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var ticks = 0

    private var peakA: Double? = null
    private var peakSinr: Double? = null
    private var peakAt = ""
    private var modeLabel = "RSRP"

    // slow-tick cache
    private var band = ""
    private var ratBase = ""
    private var lac = ""
    private var freqDl = ""
    private var freqUl = ""
    private var bwDl = ""
    private var bwUl = ""
    private var earfcnDl = ""
    private var plmnText = ""

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        val back = Ui.text(ctx, "‹  Dashboard", 15f, Ui.ACCENT, true)
        back.setOnClickListener { act.show(MainActivity.TAB_DASHBOARD) }
        root.addView(back)

        val title = Ui.text(ctx, "Arah Antena", 25f, Ui.TXT, true)
        title.setPadding(0, Ui.dp(ctx, 8), 0, 0)
        root.addView(title)
        val sub = Ui.text(ctx, "Putar / pindahkan MiFi perlahan — berhenti di angka terbaik", 12.5f, Ui.SUB)
        sub.setPadding(0, Ui.dp(ctx, 5), 0, 0)
        root.addView(sub)

        // hero: RAT + band (left), RSSI (right)
        val hero = LinearLayout(ctx)
        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.BOTTOM
        hero.setPadding(0, Ui.dp(ctx, 18), 0, 0)
        ratTv = Ui.text(ctx, "—", 32f, Color.rgb(41, 182, 246), true)
        ratTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        ratTv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hero.addView(ratTv)
        val rssiBox = LinearLayout(ctx)
        rssiBox.orientation = LinearLayout.VERTICAL
        rssiBox.gravity = Gravity.END
        val rssiLab = Ui.text(ctx, "RSSI", 12f, Color.rgb(94, 134, 184), true)
        rssiLab.gravity = Gravity.END
        rssiLab.letterSpacing = 0.25f
        rssiBox.addView(rssiLab)
        rssiTv = mono(ctx, "—", 28f, Color.rgb(41, 182, 246))
        rssiTv.gravity = Gravity.END
        rssiBox.addView(rssiTv)
        hero.addView(rssiBox)
        root.addView(hero)

        // big three
        val big3 = LinearLayout(ctx)
        big3.orientation = LinearLayout.HORIZONTAL
        big3.setPadding(0, Ui.dp(ctx, 18), 0, 0)
        val slots = listOf("RSRP", "SINR", "RSRQ")
        val labs = arrayOfNulls<TextView>(3)
        val vals = arrayOfNulls<TextView>(3)
        for (i in 0..2) {
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val lab = Ui.text(ctx, slots[i], 12f, Color.rgb(94, 134, 184), true)
            lab.letterSpacing = 0.25f
            col.addView(lab)
            val v = mono(ctx, "—", 30f, Ui.SUB)
            v.setPadding(0, Ui.dp(ctx, 5), 0, 0)
            col.addView(v)
            labs[i] = lab
            vals[i] = v
            big3.addView(col)
        }
        lab1 = labs[0]!!; val1 = vals[0]!!
        lab2 = labs[1]!!; val2 = vals[1]!!
        lab3 = labs[2]!!; val3 = vals[2]!!
        root.addView(big3)

        // session peaks
        val peak = LinearLayout(ctx)
        peak.orientation = LinearLayout.HORIZONTAL
        peak.gravity = Gravity.CENTER_VERTICAL
        peak.background = Ui.bg(Ui.CARD, 14, ctx, Ui.BORDER)
        peak.setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 11), Ui.dp(ctx, 13), Ui.dp(ctx, 11))
        val peakLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        peakLp.topMargin = Ui.dp(ctx, 16)
        peak.layoutParams = peakLp
        val peakLeft = LinearLayout(ctx)
        peakLeft.orientation = LinearLayout.VERTICAL
        peakLeft.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        peakLeft.addView(Ui.text(ctx, "TERBAIK SESI INI", 10.5f, Ui.SUB, true))
        peakTv = mono(ctx, "—", 14.5f, Ui.TXT)
        peakTv.setPadding(0, Ui.dp(ctx, 3), 0, 0)
        peakLeft.addView(peakTv)
        peak.addView(peakLeft)
        val reset = Ui.text(ctx, "RESET", 11f, Ui.SUB, true)
        reset.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 7), Ui.dp(ctx, 10), Ui.dp(ctx, 7))
        reset.background = Ui.bg(Ui.CARD, 9, ctx, Ui.BORDER)
        reset.setOnClickListener {
            peakA = null; peakSinr = null; peakAt = ""
            peakTv.text = "—"
        }
        peak.addView(reset)
        root.addView(peak)

        // info grid (2 columns)
        val keys = listOf(
            "PLMN / OPERATOR", "ENB / CELL", "LAC (DEC / HEX)", "CELL ID (DEC / HEX)",
            "DOWN / UP (MHZ)", "BANDWIDTH RX/TX", "EARFCN (DL)", "PCI"
        )
        for (r in 0 until 4) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, Ui.dp(ctx, if (r == 0) 16 else 9), 0, 0)
            for (c in 0..1) {
                val key = keys[r * 2 + c]
                val cell = LinearLayout(ctx)
                cell.orientation = LinearLayout.VERTICAL
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                cell.addView(Ui.text(ctx, key, 10f, Ui.SUB, true))
                val v = mono(ctx, "—", 13.5f, Color.rgb(201, 216, 242))
                v.setPadding(0, Ui.dp(ctx, 1), 0, 0)
                cell.addView(v)
                grid[key] = v
                row.addView(cell)
            }
            root.addView(row)
        }
        return scroll
    }

    private fun mono(ctx: Activity, s: String, size: Float, color: Int): TextView {
        val t = Ui.text(ctx, s, size, color, true)
        t.typeface = Typeface.MONOSPACE
        return t
    }

    override fun onShow() {
        visible = true
        ticks = 0
        refresh()
    }

    override fun onHide() {
        visible = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun schedule() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (visible) refresh() }, 1000L)
    }

    private data class Slow(
        val band: String, val rat: String, val lac: String, val cell: String,
        val fD: String, val fU: String, val bD: String, val bU: String,
        val ear: String, val plmn: String
    )

    private fun refresh() {
        val slow = ticks % 5 == 0
        ticks++
        val client = act.app().client()
        Bg.run({
            val sig = client.signalLight()
            var slowData: Slow? = null
            if (slow) {
                val ap = try { client.addParam() } catch (e: Exception) { emptyMap() }
                val st = try { client.status() } catch (e: Exception) { null }
                val plmn = client.plmnInfo()
                val sigObj = com.netaver.hilink.net.HilinkClient.Signal(
                    rssi = g(sig, "rssi"), rsrp = g(sig, "rsrp"), rsrq = g(sig, "rsrq"),
                    sinr = g(sig, "sinr"), rscp = g(sig, "rscp"), ecio = g(sig, "ecio")
                )
                val rat = if (st != null) Format.ratLabel(
                    st.networkType, sigObj.rsrp.isNotEmpty(), sigObj.rscp.isNotEmpty()
                ) else ""
                val numeric = g(plmn, "Numeric")
                val opName = g(plmn, "ShortName").ifEmpty { g(plmn, "FullName") }
                slowData = Slow(
                    band = g(ap, "band"), rat = rat, lac = g(ap, "lac"),
                    cell = g(ap, "cell_id"),
                    fD = g(ap, "freq1"), fU = g(ap, "freq2"),
                    bD = g(ap, "bandwidth1"), bU = g(ap, "bandwidth2"),
                    ear = g(ap, "earfcn1"),
                    plmn = when {
                        numeric.isNotEmpty() && opName.isNotEmpty() -> "$numeric · $opName"
                        numeric.isNotEmpty() -> numeric
                        else -> opName
                    }
                )
            }
            sig to slowData
        }) { r ->
            if (!visible) return@run
            r.onSuccess { (sig, slowData) ->
                if (slowData != null) {
                    if (slowData.band.isNotEmpty()) band = slowData.band
                    if (slowData.rat.isNotEmpty()) ratBase = slowData.rat
                    lac = slowData.lac
                    freqDl = slowData.fD; freqUl = slowData.fU
                    bwDl = slowData.bD; bwUl = slowData.bU
                    earfcnDl = slowData.ear
                    if (slowData.plmn.isNotEmpty()) plmnText = slowData.plmn
                    if (slowData.cell.isNotEmpty() && g(sig, "cell_id").isEmpty()) {
                        grid["CELL ID (DEC / HEX)"]?.text = cellText(slowData.cell)
                        grid["ENB / CELL"]?.text = enbText(slowData.cell)
                    }
                }
                update(sig)
            }
            schedule()
        }
    }

    private fun update(sig: Map<String, String>) {
        val rsrp = num(sig, "rsrp")
        val rscp = num(sig, "rscp")
        val sinr = num(sig, "sinr")
        val ecio = num(sig, "ecio")
        val rsrq = num(sig, "rsrq")
        val rssi = num(sig, "rssi")

        // hero
        val base = when {
            rsrp != null -> "4G-LTE"
            rscp != null -> "3G"
            ratBase.contains("LTE") -> "4G-LTE"
            ratBase.isNotEmpty() -> ratBase
            else -> "—"
        }
        ratTv.text = if (band.isNotEmpty() && base != "—") "$base · B$band" else base
        rssiTv.text = if (rssi != null) "${fmt(rssi)}dBm" else "—"

        // big three (+ peaks)
        if (rsrp != null) {
            modeLabel = "RSRP"
            lab1.text = "RSRP"
            val1.text = "${fmt(rsrp)}dBm"; val1.setTextColor(Format.rsrpColor(rsrp))
            improvePeak(rsrp, sinr)
        } else if (rscp != null) {
            modeLabel = "RSCP"
            lab1.text = "RSCP"
            val1.text = "${fmt(rscp)}dBm"; val1.setTextColor(Format.rsrpColor(rscp))
            improvePeak(rscp, ecio)
        } else {
            lab1.text = "RSRP"; val1.text = "—"; val1.setTextColor(Ui.SUB)
        }
        if (rsrp != null || rscp == null) {
            lab2.text = "SINR"
            if (sinr != null) { val2.text = "${fmt(sinr)}dB"; val2.setTextColor(Format.sinrColor(sinr)) }
            else { val2.text = "—"; val2.setTextColor(Ui.SUB) }
        } else {
            lab2.text = "Ec/No"
            if (ecio != null) { val2.text = "${fmt(ecio)}dB"; val2.setTextColor(Format.sinrColor(ecio)) }
            else { val2.text = "—"; val2.setTextColor(Ui.SUB) }
        }
        lab3.text = "RSRQ"
        if (rsrq != null) { val3.text = "${fmt(rsrq)}dB"; val3.setTextColor(Format.rsrqColor(rsrq)) }
        else { val3.text = "—"; val3.setTextColor(Ui.SUB) }

        // grid (fast-changing: cell + pci)
        val cell = g(sig, "cell_id")
        if (cell.isNotEmpty()) {
            grid["CELL ID (DEC / HEX)"]?.text = cellText(cell)
            grid["ENB / CELL"]?.text = enbText(cell)
        }
        grid["PCI"]?.text = g(sig, "pci").ifEmpty { "—" }
        grid["PLMN / OPERATOR"]?.text = plmnText.ifEmpty { "—" }
        grid["LAC (DEC / HEX)"]?.text = if (lac.isNotEmpty()) "$lac / ${Format.hexUpper(lac)}" else "—"
        grid["DOWN / UP (MHZ)"]?.text =
            if (freqDl.isNotEmpty() || freqUl.isNotEmpty()) "${mhz(freqDl)} / ${mhz(freqUl)}" else "—"
        grid["BANDWIDTH RX/TX"]?.text =
            if (bwDl.isNotEmpty() || bwUl.isNotEmpty()) "${mhz(bwDl)} / ${mhz(bwUl)} MHz" else "—"
        grid["EARFCN (DL)"]?.text = earfcnDl.ifEmpty { "—" }
    }

    private fun improvePeak(a: Double, b: Double?) {
        var improved = false
        if (peakA == null || a > peakA!!) { peakA = a; improved = true }
        if (b != null && (peakSinr == null || b > peakSinr!!)) { peakSinr = b; improved = true }
        if (improved) {
            peakAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val sb = StringBuilder("$modeLabel ${fmt(peakA!!)}")
            if (peakSinr != null) sb.append(" · SINR ${fmt(peakSinr!!)}")
            if (modeLabel == "RSCP" && peakSinr != null) {
                // label the second peak correctly on 3G
                sb.setLength(0)
                sb.append("RSCP ${fmt(peakA!!)} · Ec/No ${fmt(peakSinr!!)}")
            }
            sb.append(" · $peakAt")
            peakTv.text = sb.toString()
        }
    }

    private fun cellText(cell: String): String {
        val hex = Format.hexUpper(cell)
        return if (hex.isNotEmpty()) "$cell / $hex" else cell
    }

    private fun enbText(cell: String): String {
        val (enb, sector) = Format.enbSector(cell)
        return if (enb.isNotEmpty()) "$enb / $sector" else "—"
    }

    private fun g(m: Map<String, String>, key: String): String =
        m.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim() ?: ""

    private fun num(m: Map<String, String>, key: String): Double? =
        Format.stripUnit(g(m, key)).toDoubleOrNull()

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun mhz(s: String): String {
        val d = Format.stripUnit(s).toDoubleOrNull() ?: return s.ifEmpty { "—" }
        return fmt(d)
    }
}

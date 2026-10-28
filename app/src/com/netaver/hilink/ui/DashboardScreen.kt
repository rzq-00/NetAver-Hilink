package com.netaver.hilink.ui

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.Format
import com.netaver.hilink.net.HilinkClient

/** Dashboard: connection hero, signal grid, traffic, quick actions. Auto-refresh while visible. */
class DashboardScreen(private val act: MainActivity) : Screen {
    private lateinit var root: LinearLayout
    private lateinit var statusSub: TextView
    private lateinit var batteryV: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var dataBtn: TextView
    private var lastConnected = false
    private var toggling = false
    private lateinit var wanIpView: TextView
    private lateinit var rsrpBig: TextView
    private lateinit var rsrpUnit: TextView
    private lateinit var rsrpGrade: TextView
    private lateinit var barsRow: LinearLayout
    private val cells = mutableMapOf<String, TextView>()
    private lateinit var trafficLine: TextView
    private lateinit var monthLine: TextView
    private lateinit var speedLine: TextView
    private lateinit var footNote: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var loading = false

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        scroll.addView(root)

        // header
        val head = LinearLayout(ctx); head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(ctx)
        logo.setImageResource(act.logoRes())
        logo.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 42), Ui.dp(ctx, 42))
        head.addView(logo)
        val ht = LinearLayout(ctx); ht.orientation = LinearLayout.VERTICAL
        ht.setPadding(Ui.dp(ctx, 11), 0, 0, 0)
        ht.addView(Ui.text(ctx, "NetAver HiLink", 19f, Ui.TXT, true))
        statusSub = Ui.text(ctx, "Menghubungkan…", 12.5f, Ui.SUB)
        ht.addView(statusSub)
        head.addView(ht)
        // battery block: label + value as ONE right-aligned unit (both MATCH_PARENT
        // width + text gravity END, so their right edges always coincide)
        val batBox = LinearLayout(ctx); batBox.orientation = LinearLayout.VERTICAL
        batBox.gravity = Gravity.END
        batBox.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val batLab = Ui.text(ctx, "BATERAI", 11.5f, Ui.SUB)
        batLab.gravity = Gravity.END
        batLab.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        batBox.addView(batLab)
        batteryV = Ui.text(ctx, "—", 17f, Ui.GREEN, true)
        batteryV.gravity = Gravity.END
        batteryV.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        batBox.addView(batteryV)
        head.addView(batBox)
        root.addView(head)

        // hero
        val hero = Ui.card(ctx)
        val heroTop = LinearLayout(ctx); heroTop.orientation = LinearLayout.HORIZONTAL
        val heroTxt = LinearLayout(ctx); heroTxt.orientation = LinearLayout.VERTICAL
        heroTxt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        heroTitle = Ui.text(ctx, "—", 19f, Ui.TXT, true)
        heroSub = Ui.text(ctx, "—", 13f, Ui.SUB)
        heroSub.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val heroSubRow = LinearLayout(ctx); heroSubRow.orientation = LinearLayout.HORIZONTAL
        heroSubRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        heroSubRow.addView(heroSub)
        // vc35: the duration row is text-only again (the ⇄ toggle moved to
        // the WAN IP row below). NOT the PERANGKAT card (that feature was
        // not in the mockup and is not part of this version).
        heroTxt.addView(heroTitle); heroTxt.addView(heroSubRow)
        heroTop.addView(heroTxt)
        barsRow = LinearLayout(ctx); barsRow.orientation = LinearLayout.HORIZONTAL
        barsRow.gravity = Gravity.BOTTOM
        heroTop.addView(barsRow)
        hero.addView(heroTop)
        val bigRow = LinearLayout(ctx); bigRow.orientation = LinearLayout.HORIZONTAL
        bigRow.setPadding(0, Ui.dp(ctx, 12), 0, 0)
        rsrpBig = Ui.text(ctx, "—", 31f, Ui.TXT, true)
        bigRow.addView(rsrpBig)
        rsrpUnit = Ui.text(ctx, " dBm RSRP", 14f, Ui.SUB)
        rsrpUnit.setPadding(0, Ui.dp(ctx, 12), 0, 0)
        bigRow.addView(rsrpUnit)
        rsrpGrade = Ui.text(ctx, "", 15f, Ui.GREEN, true)
        val glp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        rsrpGrade.layoutParams = glp
        rsrpGrade.gravity = Gravity.END
        rsrpGrade.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        bigRow.addView(rsrpGrade)
        hero.addView(bigRow)
        // WAN IP on its own row below the RSRP row (vc33: moved out of the
        // duration row, where the IP crowded the duration and forced it to
        // wrap). vc35: the ⇄ data toggle lives HERE at the far right end,
        // right-aligned in a fixed 36dp slot SEPARATE from the WAN IP text
        // (the text stays centered in its weighted cell), so the icon's
        // position never shifts when the IP text length changes or becomes
        // "—". Tapping toggles via the existing dataswitch API; each tap is
        // explicit, so no extra confirmation dialog.
        val wanIpRow = LinearLayout(ctx); wanIpRow.orientation = LinearLayout.HORIZONTAL
        wanIpRow.gravity = Gravity.CENTER_VERTICAL
        wanIpRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wanIpRow.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        wanIpView = Ui.text(ctx, "IP WAN —", 13f, Ui.TXT)
        wanIpView.gravity = Gravity.CENTER
        wanIpView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        wanIpRow.addView(wanIpView)
        dataBtn = Ui.text(ctx, "⇄", 18f, Ui.SUB, true)
        dataBtn.gravity = Gravity.CENTER
        dataBtn.background = Ui.bg(Ui.CARD2, 18, ctx, Ui.BORDER)
        dataBtn.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36))
        dataBtn.setOnClickListener { toggleData() }
        wanIpRow.addView(dataBtn)
        hero.addView(wanIpRow)
        root.addView(hero)

        // signal grid
        val grid = LinearLayout(ctx); grid.orientation = LinearLayout.VERTICAL
        grid.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        (grid.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 12)
        val keys = listOf(listOf("RSRQ", "SINR", "RSSI"), listOf("PCI", "CELL ID", "PERANGKAT"))
        for (rowKeys in keys) {
            val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            for ((idx, k) in rowKeys.withIndex()) {
                val cell = LinearLayout(ctx); cell.orientation = LinearLayout.VERTICAL
                cell.background = Ui.bg(Ui.CARD, 12, ctx, Ui.BORDER)
                cell.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (idx > 0) lp.leftMargin = Ui.dp(ctx, 10)
                cell.layoutParams = lp
                cell.addView(Ui.text(ctx, k, 11.5f, Ui.SUB))
                val v = Ui.text(ctx, "—", 17f, Ui.TXT, true)
                if (k == "CELL ID") {
                    // 9-digit CELL ID must stay on ONE line or its card grows
                    // taller than PCI/PERANGKAT. 14sp fits 9 bold digits in the
                    // card's inner width on a 360dp screen; singleLine forbids wrap.
                    v.setSingleLine(true)
                    v.textSize = 14f
                }
                cells[k] = v
                cell.addView(v)
                row.addView(cell)
            }
            (row.layoutParams as LinearLayout.LayoutParams).bottomMargin = Ui.dp(ctx, 10)
            grid.addView(row)
        }
        root.addView(grid)

        // traffic
        val traf = Ui.card(ctx)
        traf.layoutParams = (traf.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0 }
        val th = LinearLayout(ctx); th.orientation = LinearLayout.HORIZONTAL
        val thL = Ui.text(ctx, "SESI INI", 11.5f, Ui.SUB)
        thL.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        th.addView(thL)
        th.addView(Ui.text(ctx, "BULAN INI", 11.5f, Ui.SUB))
        traf.addView(th)
        val tl = LinearLayout(ctx); tl.orientation = LinearLayout.HORIZONTAL
        tl.setPadding(0, Ui.dp(ctx, 5), 0, 0)
        trafficLine = Ui.text(ctx, "—", 16.5f, Ui.TXT, true)
        trafficLine.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tl.addView(trafficLine)
        monthLine = Ui.text(ctx, "—", 16.5f, Ui.TXT, true)
        tl.addView(monthLine)
        traf.addView(tl)
        speedLine = Ui.text(ctx, "", 12.5f, Ui.SUB)
        speedLine.setPadding(0, Ui.dp(ctx, 6), 0, 0)
        traf.addView(speedLine)
        root.addView(traf)

        // quick actions
        val qa = LinearLayout(ctx); qa.orientation = LinearLayout.HORIZONTAL
        qa.setPadding(0, Ui.dp(ctx, 12), 0, 0)
        val actions = listOf("📶\nMode" to MainActivity.TAB_MODE, "🎯\nBand" to MainActivity.TAB_BAND,
            "✉️\nSMS" to MainActivity.TAB_SMS, "📡\nAntena" to MainActivity.TAB_POINTING, "🖥️\nPerangkat" to MainActivity.TAB_DEVICES)
        for ((idx, pair) in actions.withIndex()) {
            val q = Ui.text(ctx, pair.first, 12f, Color.rgb(198, 207, 226))
            q.gravity = Gravity.CENTER
            q.setPadding(0, Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11))
            q.background = Ui.bg(Ui.CARD, 12, ctx, Ui.BORDER)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (idx > 0) lp.leftMargin = Ui.dp(ctx, 10)
            q.layoutParams = lp
            val tab = pair.second
            q.setOnClickListener { act.show(tab) }
            qa.addView(q)
        }
        root.addView(qa)

        val restart = Ui.button(ctx, "RESTART ROUTER", primary = false)
        restart.setOnClickListener {
            Ui.confirm(act, "Restart router?", "Koneksi internet akan putus sekitar 1 menit selama router menyala ulang.", "Restart") {
                Bg.run({ act.app().client().deviceControl(1) }) { r ->
                    r.onSuccess { Ui.toast(ctx, "Perintah restart terkirim") }
                    r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
                }
            }
        }
        root.addView(restart)

        footNote = Ui.text(ctx, "", 12f, Ui.FAINT)
        footNote.gravity = Gravity.CENTER
        footNote.setPadding(0, Ui.dp(ctx, 14), 0, 0)
        root.addView(footNote)
        return scroll
    }

    override fun onShow() {
        visible = true
        refresh()
        schedule()
    }

    override fun onHide() {
        visible = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun schedule() {
        handler.removeCallbacksAndMessages(null)
        val secs = act.app().prefs.refreshSecs
        if (secs <= 0 || !visible) return
        handler.postDelayed({ if (visible) { refresh(); schedule() } }, secs * 1000L)
    }

    /** vc35: icon state is read from the BACKGROUND — solid green + white
     *  glyph = connected (data on), dark card + gray glyph = disconnected. */
    private fun setDataBtnState(connected: Boolean) {
        if (connected) {
            dataBtn.background = Ui.bg(Ui.GREEN, 18, act)
            dataBtn.setTextColor(android.graphics.Color.WHITE)
        } else {
            dataBtn.background = Ui.bg(Ui.CARD2, 18, act, Ui.BORDER)
            dataBtn.setTextColor(Ui.SUB)
        }
    }

    /** Toggle mobile data via the existing dataswitch API, then refresh
     *  so state, duration and WAN IP update. Guarded against double-taps;
     *  failures toast and leave the icon unchanged. */
    private fun toggleData() {
        if (toggling) return
        toggling = true
        val target = !lastConnected
        Bg.run({ act.app().client().setDataSwitch(target) }) { r ->
            toggling = false
            r.onSuccess { refresh() }
            r.onFailure { Ui.toast(act, "Gagal: ${it.message}") }
        }
    }

    private fun refresh() {
        if (loading) return
        loading = true
        val client = act.app().client()
        Bg.run({
            client.ensureSession()
            val st = client.status()
            val sg = client.signal()
            val tr = client.traffic()
            val mo = client.monthStat()
            val op = client.operatorName()
            val hosts = try { client.hosts().size } catch (e: Exception) { -1 }
            val unread = client.unreadSms()
            val mask = try { client.netMode().lteBand } catch (e: Exception) { "" }
            Triple(Triple(st, sg, tr), Triple(mo, op, hosts), Pair(unread, mask))
        }) { res ->
            loading = false
            res.onSuccess { (a, b, extra) ->
                val (st, sg, tr) = a
                val (mo, op, hostCount) = b
                val (unread, mask) = extra
                render(st, sg, tr, mo, op, hostCount, unread, mask)
            }
            res.onFailure { e ->
                statusSub.text = "● Gagal terhubung · ${act.app().prefs.routerIp}"
                statusSub.setTextColor(Ui.RED)
                wanIpView.text = "IP WAN —"
                setDataBtnState(false)
                footNote.text = e.message ?: "Tidak terjangkau"
            }
        }
    }

    private fun render(st: HilinkClient.Status, sg: HilinkClient.Signal, tr: HilinkClient.Traffic,
                       mo: HilinkClient.MonthStat, op: String, hostCount: Int, unread: Int, lteMask: String) {
        val ctx = act
        statusSub.text = "● Terhubung · ${act.app().prefs.routerIp}"
        statusSub.setTextColor(Ui.GREEN)
        batteryV.text = if (st.batteryLevel.isNotEmpty()) st.batteryLevel + "%" else "—"
        val typeLabel = Format.ratLabel(st.networkType, sg.rsrp.isNotEmpty() || sg.rsrq.isNotEmpty(), sg.rscp.isNotEmpty())
        val bandNum = if (sg.rsrp.isNotEmpty() || sg.rsrq.isNotEmpty())
            sg.band.toIntOrNull() ?: com.netaver.hilink.core.Bands.parseMask(lteMask).singleOrNull()
        else null
        heroTitle.text = listOf(op.ifEmpty { "Operator" }, typeLabel, bandNum?.let { "B$it" } ?: "")
            .filter { it.isNotEmpty() }.joinToString(" · ")
        val connTxt = when (st.connectionStatus) {
            "901" -> "Terhubung"
            "900" -> "Menghubungkan…"
            "902" -> "Terputus"
            "903" -> "Memutuskan…"
            else -> "Status " + st.connectionStatus
        }
        heroSub.text = "$connTxt · durasi ${Format.duration(tr.connectTimeSec)}" +
            if (st.roaming == "1") " · ROAMING" else ""
        // vc35: icon state follows the connection via its BACKGROUND —
        // solid green + white glyph = connected (data on), dark card + gray
        // glyph = disconnected. lastConnected drives the toggle direction.
        lastConnected = st.connectionStatus == "901"
        setDataBtnState(lastConnected)
        wanIpView.text = if (st.wanIp.isNotEmpty()) "IP WAN ${st.wanIp}" else "IP WAN —"
        val rsrp = sg.rsrp.toIntOrNull()
        val noSignal = rsrp == null && sg.rscp.isEmpty() && sg.rssi.isEmpty()
        if (noSignal) {
            rsrpBig.text = "No Signal"
            rsrpBig.setTextColor(Ui.RED)
            rsrpBig.textSize = 23f
            rsrpUnit.visibility = View.GONE
            rsrpGrade.text = ""
            drawBars(0, Ui.SUB)
        } else if (rsrp != null) {
            rsrpBig.text = rsrp.toString()
            rsrpBig.setTextColor(Format.rsrpColor(rsrp.toDouble()))
            rsrpBig.textSize = 31f
            rsrpUnit.visibility = View.VISIBLE
            val (label, bars) = Format.gradeRsrp(rsrp)
            rsrpGrade.text = label
            rsrpGrade.setTextColor(Format.rsrpColor(rsrp.toDouble()))
            // vc34: filled bars colored by RSRP bands (agree with the RSRP
            // number's quality tier): >= -90 green, -110..-90 yellow, < -110 red.
            val barColor = when {
                rsrp >= -90 -> Ui.GREEN
                rsrp >= -110 -> Ui.AMBER
                else -> Ui.RED
            }
            drawBars(bars, barColor)
        } else {
            rsrpBig.text = sg.rssi.ifEmpty { "—" }
            rsrpBig.setTextColor(colorOrDefault(sg.rssi, Format::rssiColor))
            rsrpBig.textSize = 31f
            rsrpUnit.visibility = View.VISIBLE
            rsrpGrade.text = if (sg.rscp.isNotEmpty()) "3G · RSCP " + sg.rscp else ""
            rsrpGrade.setTextColor(Ui.TXT)
            // vc34: no RSRP — fall back to the SignalIcon level bands
            // (5-6 green, 3-4 yellow, 0-2 red). Never crashes on missing data.
            val icon = st.signalIcon.toIntOrNull() ?: 0
            val iconColor = when {
                icon >= 5 -> Ui.GREEN
                icon >= 3 -> Ui.AMBER
                else -> Ui.RED
            }
            drawBars(icon, iconColor)
        }
        cells["RSRQ"]?.text = dash(sg.rsrq, " dB")
        cells["SINR"]?.text = dash(sg.sinr, " dB")
        cells["RSSI"]?.text = dash(sg.rssi, " dBm")
        cells["PCI"]?.text = sg.pci.ifEmpty { "—" }
        cells["CELL ID"]?.text = sg.cellId.filter { !it.isWhitespace() }.ifEmpty { "—" }
        val radioCells = listOf("RSRQ", "SINR", "RSSI", "PCI", "CELL ID")
        if (noSignal) {
            for (k in radioCells) {
                cells[k]?.text = "No Signal"
                cells[k]?.setTextColor(Ui.RED)
                cells[k]?.textSize = 12.5f
            }
        } else {
            cells["RSRQ"]?.setTextColor(colorOrDefault(sg.rsrq, Format::rsrqColor))
            cells["SINR"]?.setTextColor(colorOrDefault(sg.sinr, Format::sinrColor))
            cells["RSSI"]?.setTextColor(colorOrDefault(sg.rssi, Format::rssiColor))
            cells["PCI"]?.setTextColor(Ui.TXT)
            cells["CELL ID"]?.setTextColor(Ui.TXT)
            for (k in radioCells) {
                if (k != "CELL ID") cells[k]?.textSize = 17f
            }
            cells["CELL ID"]?.textSize = 14f
        }
        cells["PERANGKAT"]?.text = if (hostCount >= 0) "$hostCount WiFi" else "—"
        trafficLine.text = "↓ ${Format.bytes(tr.curDown)} · ↑ ${Format.bytes(tr.curUp)}"
        monthLine.text = Format.bytes(mo.down + mo.up)
        speedLine.text = "Total kumulatif: ↓ ${Format.bytes(tr.totalDown)} · ↑ ${Format.bytes(tr.totalUp)}\nKecepatan sekarang: ↓ ${Format.bytes(tr.downRate)}/s · ↑ ${Format.bytes(tr.upRate)}/s" +
            if (unread > 0) " · ✉️ $unread SMS belum dibaca" else ""
        val secs = act.app().prefs.refreshSecs
        footNote.text = (if (secs > 0) "Diperbarui tiap $secs detik · " else "Perbarui manual (geser tab) · ") +
            "sesi login tunggal, dipakai bersama semua layar"
    }

    private fun dash(v: String, unit: String): String = if (v.isEmpty()) "—" else v + unit

    /** Metric string -> its quality color, or default text color when missing/non-numeric. Never crashes. */
    private fun colorOrDefault(v: String, f: (Double) -> Int): Int =
        v.toDoubleOrNull()?.let(f) ?: Ui.TXT

    /** Signal bars: [filled] of 5 bars in [color]; unfilled bars stay gray.
     *  No-signal (filled = 0) draws all bars gray. */
    private fun drawBars(filled: Int, color: Int) {
        barsRow.removeAllViews()
        val heights = listOf(9, 15, 21, 27, 30)
        for (i in 0 until 5) {
            val b = View(act)
            val lp = LinearLayout.LayoutParams(Ui.dp(act, 7), Ui.dp(act, heights[i]))
            if (i > 0) lp.leftMargin = Ui.dp(act, 3)
            b.layoutParams = lp
            b.background = Ui.bg(
                if (i < filled) color else Color.rgb(42, 51, 80), 2, act)
            barsRow.addView(b)
        }
    }
}

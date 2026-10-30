package com.netaver.hilink.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Analisa Channel WiFi — layar baru (dibuka dari tab WiFi).
 * Memindai jaringan sekitar memakai WiFi HP sendiri (bukan router),
 * menilai kepadatan tiap channel 2.4 GHz (1–13) dengan bobot tumpang
 * tindih channel tetangga, lalu merekomendasikan channel tersepi.
 * Hasil divisualkan sebagai bar grafik ringkas (bukan daftar 13 baris).
 */
class WifiAnalisaScreen(private val act: MainActivity) : Screen, MainActivity.PermResult {

    companion object {
        const val REQ_LOC = 4201
        private const val SCAN_TIMEOUT_MS = 12000L
        private const val COOLDOWN_MS = 8000L
    }

    private lateinit var scanBtn: TextView
    private lateinit var resultHead: TextView
    private lateinit var chart: ChannelChartView
    private lateinit var detailCard: LinearLayout
    private lateinit var recBox: LinearLayout
    private var selectedCh = 0
    private var lastByCh: Map<Int, List<Ap>> = emptyMap()
    private val scanTimes = ArrayDeque<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var scanPending = false
    private var scannedOnce = false

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx); scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)
        val back = Ui.text(ctx, "‹  Analisa Channel", 15f, Ui.ACCENT, true)
        back.setPadding(0, 0, 0, Ui.dp(ctx, 10))
        back.setOnClickListener { act.show(MainActivity.TAB_WIFI) }
        root.addView(back)
        root.addView(Ui.text(ctx, "Channel WiFi Sekitar", 24f, Ui.TXT, true))
        val sub = Ui.text(ctx, "Dipindai dari WiFi HP", 13.5f, Ui.SUB)
        sub.setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 6))
        root.addView(sub)
        val note = Ui.text(ctx, "Butuh izin lokasi & GPS aktif untuk memindai.", 12.5f, Ui.SUB)
        note.setPadding(0, 0, 0, Ui.dp(ctx, 10))
        root.addView(note)
        scanBtn = Ui.button(ctx, "PINDAI")
        scanBtn.setOnClickListener { onScanTap() }
        root.addView(scanBtn)
        val card = Ui.card(ctx)
        resultHead = Ui.text(ctx, "Belum ada hasil — tekan PINDAI.", 13f, Ui.SUB)
        card.addView(resultHead)
        val legend = Ui.text(ctx, "tinggi bar = jumlah AP", 11.5f, Ui.SUB)
        legend.setPadding(0, Ui.dp(ctx, 4), 0, 0)
        card.addView(legend)
        chart = ChannelChartView(ctx)
        card.addView(chart)
        val tapHint = Ui.text(ctx, "Ketuk bar untuk melihat SSID-nya", 11.5f, Ui.SUB)
        tapHint.setPadding(0, Ui.dp(ctx, 6), 0, 0)
        card.addView(tapHint)
        detailCard = LinearLayout(ctx); detailCard.orientation = LinearLayout.VERTICAL
        detailCard.visibility = View.GONE
        val dlp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dlp.topMargin = Ui.dp(ctx, 10)
        detailCard.layoutParams = dlp
        card.addView(detailCard)
        chart.onBarTap = { ch -> onBarTap(ch) }
        recBox = LinearLayout(ctx); recBox.orientation = LinearLayout.VERTICAL
        recBox.visibility = View.GONE
        val rlp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = Ui.dp(ctx, 10)
        recBox.layoutParams = rlp
        card.addView(recBox)
        root.addView(card)
        val tail = Ui.text(ctx,
            "Tombol ‘Pakai’ memilih channel di tab WiFi — terapkan dari sana.",
            12.5f, Ui.SUB)
        tail.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        root.addView(tail)
        return scroll
    }

    override fun onHide() {
        scanPending = false
        cleanupReceiver()
    }

    // ---- bar grafik channel 1–13 ----

    private inner class ChannelChartView(ctx: Context) : View(ctx) {
        private var counts = IntArray(14)
        private var rec = 1
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 38, 62); strokeWidth = 1f
        }
        private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(60, 70, 100); strokeWidth = 2f
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = Ui.dp(ctx, 11).toFloat(); textAlign = Paint.Align.CENTER
        }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = Ui.dp(ctx, 11).toFloat(); textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        fun setData(c: IntArray, r: Int) {
            counts = c; rec = r; invalidate()
        }

        // ---- ketuk bar: pilih channel, tampilkan daftar SSID ----
        var onBarTap: ((Int) -> Unit)? = null
        private var sel = 0
        private var tapPadL = 0f
        private var tapSlot = 0f
        private var tapTotal = 0
        private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = Ui.dp(ctx, 2).toFloat()
        }

        fun setSelected(ch: Int) { sel = ch; invalidate() }
        fun clearSelection() { sel = 0; invalidate() }

        private fun channelAt(x: Float): Int? {
            if (tapTotal == 0 || tapSlot <= 0f) return null
            val ch = ((x - tapPadL) / tapSlot).toInt() + 1
            return if (ch in 1..13) ch else null
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                performClick()
                channelAt(e.x)?.let { onBarTap?.invoke(it) }
                return true
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                Ui.dp(context, 200))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val padL = Ui.dp(context, 22).toFloat()
            val padR = Ui.dp(context, 8).toFloat()
            val padT = Ui.dp(context, 24).toFloat()
            val padB = Ui.dp(context, 26).toFloat()
            val plotW = w - padL - padR
            val plotH = h - padT - padB
            val base = padT + plotH
            val total = (1..13).sumOf { counts[it] }
            if (total == 0) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Ui.SUB; textSize = Ui.dp(context, 13).toFloat()
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("tidak ada jaringan terdeteksi", w / 2, h / 2, p)
                return
            }
            val maxN = (1..13).maxOf { counts[it] }.coerceAtLeast(1)
            for (g in 1..maxN) {
                val gy = base - plotH * g / maxN
                canvas.drawLine(padL, gy, padL + plotW, gy, gridPaint)
                labelPaint.color = Ui.SUB
                canvas.drawText(g.toString(), padL - Ui.dp(context, 11).toFloat(),
                    gy + Ui.dp(context, 4).toFloat(), labelPaint)
            }
            canvas.drawLine(padL, base, padL + plotW, base, axisPaint)
            val slot = plotW / 13
            tapPadL = padL; tapSlot = slot; tapTotal = total
            val barW = slot * 0.55f
            val rad = Ui.dp(context, 5).toFloat()
            for (ch in 1..13) {
                val cx = padL + slot * (ch - 1) + slot / 2
                val n = counts[ch]
                val isRec = ch == rec
                if (n > 0) {
                    val bh = plotH * n / maxN
                    barPaint.color = if (isRec) Ui.GREEN else Ui.ACCENT
                    canvas.drawRoundRect(cx - barW / 2, base - bh, cx + barW / 2,
                        base, rad, rad, barPaint)
                    valuePaint.color = if (isRec) Ui.GREEN else Ui.ACCENT
                    canvas.drawText(n.toString(), cx,
                        base - bh - Ui.dp(context, 5).toFloat(), valuePaint)
                } else {
                    canvas.drawLine(cx - barW / 2, base - 2, cx + barW / 2,
                        base - 2, axisPaint)
                }
                labelPaint.color = if (isRec) Ui.GREEN else Ui.SUB
                canvas.drawText(ch.toString(), cx,
                    base + Ui.dp(context, 16).toFloat(), labelPaint)
                if (ch == sel) {
                    val pad = Ui.dp(context, 4).toFloat()
                    val bh = if (n > 0) plotH * n / maxN else 0f
                    val topEdge = if (n > 0) base - bh else base - 2
                    canvas.drawRoundRect(cx - barW / 2 - pad, topEdge - pad,
                        cx + barW / 2 + pad, base + pad, rad + pad, rad + pad, selPaint)
                }
            }
        }
    }

    // ---- scan flow ----

    private fun onScanTap() {
        if (scanPending) return
        if (act.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Ui.confirm(act, "Izin lokasi",
                "Android mewajibkan izin lokasi untuk memindai WiFi. " +
                    "Hasil pindai hanya dipakai di HP ini untuk memilih channel.",
                "Izinkan") {
                act.requestPerm(Manifest.permission.ACCESS_FINE_LOCATION, REQ_LOC, this)
            }
            return
        }
        val lm = act.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locOn = try {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) { true }
        if (!locOn) {
            Ui.toast(act, "Aktifkan GPS/layanan lokasi di HP agar hasil pindai muncul.")
            return
        }
        val wm = act.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm == null || !wm.isWifiEnabled) {
            Ui.toast(act, "WiFi HP mati — nyalakan dulu untuk memindai.")
            return
        }
        // Batas Android 9+: maks ~4 pindai per 2 menit per aplikasi.
        val now = System.currentTimeMillis()
        while (scanTimes.isNotEmpty() && now - scanTimes.first() > 120_000) scanTimes.removeFirst()
        if (scanTimes.size >= 4) {
            Ui.toast(act, "Batas pindai Android tercapai — tunggu sebentar sebelum pindai lagi.")
            return
        }
        startScan(wm)
    }

    private fun startScan(wm: WifiManager) {
        cleanupReceiver()
        scanPending = true
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onScanDone(wm)
        }
        receiver = r
        // API 33+ mewajibkan flag export pada registerReceiver.
        if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") act.registerReceiver(r, filter)
        val ok = try { wm.startScan() } catch (e: SecurityException) { false }
        if (!ok) {
            scanPending = false
            cleanupReceiver()
            Ui.toast(act, "Pindai ditolak sistem (batas Android) — coba lagi sebentar.")
            return
        }
        handler.postDelayed({
            if (scanPending) {
                scanPending = false
                cleanupReceiver()
                Ui.toast(act, "Pindai timeout — coba lagi.")
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun onScanDone(wm: WifiManager) {
        if (!scanPending) return
        scanPending = false
        cleanupReceiver()
        scanTimes.addLast(System.currentTimeMillis())
        val results = try { wm.scanResults } catch (e: SecurityException) { emptyList() }
        renderResults(results)
        scannedOnce = true
        scanBtn.isEnabled = false
        scanBtn.alpha = 0.55f
        scanBtn.text = "TUNGGU…"
        handler.postDelayed({
            scanBtn.isEnabled = true
            scanBtn.alpha = 1f
            scanBtn.text = "PINDAI ULANG"
        }, COOLDOWN_MS)
    }

    private fun cleanupReceiver() {
        receiver?.let { try { act.unregisterReceiver(it) } catch (e: Exception) { /* sudah lepas */ } }
        receiver = null
    }

    override fun onPermResult(requestCode: Int, granted: Boolean) {
        if (requestCode != REQ_LOC) return
        if (granted) onScanTap()
        else Ui.toast(act, "Izin lokasi ditolak — pindai WiFi tidak bisa jalan.")
    }

    // ---- analysis + rendering ----

    private data class Ap(val ch: Int, val level: Int, val ssid: String, val caps: String)

    private fun renderResults(results: List<ScanResult>) {
        val ownSsid = act.wifiScreen()?.currentSsid().orEmpty()
        val aps = results.mapNotNull { r ->
            val f = r.frequency
            if (f < 2400 || f > 2500) return@mapNotNull null
            val ch = (f - 2407) / 5
            if (ch !in 1..13) return@mapNotNull null
            val ssid = r.SSID?.trim()?.removeSurrounding("\"").orEmpty()
            if (ssid.isNotEmpty() && ssid == ownSsid) return@mapNotNull null // SSID router sendiri
            Ap(ch, r.level, ssid, r.capabilities.orEmpty())
        }
        val count = IntArray(14)
        val best = IntArray(14) { Int.MIN_VALUE }
        aps.forEach { a ->
            count[a.ch]++
            if (a.level > best[a.ch]) best[a.ch] = a.level
        }
        // Skor kepadatan: tumpang tindih channel tetangga × bobot sinyal.
        val score = DoubleArray(14)
        for (ch in 1..13) {
            var s = 0.0
            for (a in aps) {
                val overlap = maxOf(0.0, 1.0 - abs(ch - a.ch) / 3.0)
                if (overlap > 0) s += overlap * ((a.level + 100) / 45.0).coerceIn(0.0, 1.0)
            }
            score[ch] = s
        }
        val rec = (1..13).sortedWith(compareBy(
            { score[it] },
            { if (it == 1 || it == 6 || it == 11) 0 else 1 },
            { it },
        )).first()

        val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        resultHead.text = "${aps.size} jaringan ditemukan · pindai $hhmm"
        chart.setData(count, rec)
        // Pindai baru: seleksi & kartu detail di-reset (tersembunyi sampai ketuk).
        lastByCh = aps.groupBy { it.ch }
        selectedCh = 0
        chart.clearSelection()
        detailCard.visibility = View.GONE

        recBox.removeAllViews()
        recBox.visibility = View.VISIBLE
        recBox.background = Ui.bg(Ui.CARD, 12, act, Ui.GREEN)
        recBox.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
        recBox.addView(Ui.text(act, "★ Rekomendasi: Channel $rec", 15f, Ui.GREEN, true))
        val n = count[rec]
        val reason = if (n == 0) "Channel kosong — tidak ada AP terdeteksi"
                     else "$n AP di sekitar (terkuat ${best[rec]} dBm)"
        val rs = Ui.text(act, reason, 12.5f, Ui.SUB)
        rs.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 10))
        recBox.addView(rs)
        // Tombol Pakai: rata tengah horizontal (revisi mockup v3).
        val use = Ui.button(act, "Pakai channel $rec")
        val ulp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ulp.gravity = Gravity.CENTER_HORIZONTAL
        ulp.topMargin = 0
        use.layoutParams = ulp
        use.setOnClickListener {
            val ok = act.wifiScreen()?.selectChannel24(rec) == true
            act.show(MainActivity.TAB_WIFI)
            if (!ok) Ui.toast(act, "Layar WiFi belum siap — pilih channel manual.")
        }
        recBox.addView(use)
    }

    // ---- detail SSID per channel (ketuk bar) ----

    private fun onBarTap(ch: Int) {
        selectedCh = if (selectedCh == ch) 0 else ch
        chart.setSelected(selectedCh)
        if (selectedCh == 0) detailCard.visibility = View.GONE
        else renderDetail(selectedCh)
    }

    private fun renderDetail(ch: Int) {
        val list = (lastByCh[ch] ?: emptyList()).sortedByDescending { it.level }
        detailCard.removeAllViews()
        detailCard.background = Ui.bg(Ui.CARD2, 12, act)
        detailCard.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
        detailCard.addView(Ui.text(act, "Channel $ch · ${list.size} jaringan", 13.5f, Ui.TXT, true))
        if (list.isEmpty()) {
            val e = Ui.text(act, "tidak ada AP di channel ini", 12.5f, Ui.SUB)
            e.setPadding(0, Ui.dp(act, 6), 0, 0)
            detailCard.addView(e)
        } else {
            list.forEachIndexed { idx, a ->
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                val rp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rp.topMargin = Ui.dp(act, if (idx == 0) 8 else 6)
                row.layoutParams = rp
                val name = Ui.text(act, a.ssid.ifEmpty { "(tersembunyi)" }, 14f, Ui.TXT)
                name.maxLines = 1
                name.ellipsize = android.text.TextUtils.TruncateAt.END
                name.layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val info = Ui.text(act, "${a.level} dBm · ${secLabel(a.caps)}", 12.5f, Ui.SUB)
                row.addView(name)
                row.addView(info)
                detailCard.addView(row)
            }
        }
        detailCard.visibility = View.VISIBLE
    }

    private fun secLabel(caps: String): String {
        val c = caps.uppercase(Locale.getDefault())
        return when {
            "WPA3" in c -> "WPA3"
            "WPA2" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            else -> "Terbuka"
        }
    }
}

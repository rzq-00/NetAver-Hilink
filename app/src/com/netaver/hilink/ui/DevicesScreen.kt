package com.netaver.hilink.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.Format

/** Connected devices + device info + device control (restart / power off / factory reset / clear stats). */
class DevicesScreen(private val act: MainActivity) : Screen {
    private lateinit var hostsBox: LinearLayout
    private lateinit var infoBox: LinearLayout
    private lateinit var antennaBox: LinearLayout
    private lateinit var subTv: TextView
    private var loading = false
    private var imeiRevealed = false
    private var imeiFull = ""
    private var imeiValueTv: TextView? = null

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        root.addView(Ui.text(ctx, "Perangkat", 24f, Ui.TXT, true))
        subTv = Ui.text(ctx, "Memuat…", 13.5f, Ui.SUB)
        subTv.setPadding(0, Ui.dp(ctx, 3), 0, 0)
        root.addView(subTv)

        hostsBox = LinearLayout(ctx)
        hostsBox.orientation = LinearLayout.VERTICAL
        hostsBox.background = Ui.bg(Ui.CARD2, 12, ctx, Ui.BORDER)
        hostsBox.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        (hostsBox.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 14)
        root.addView(hostsBox)

        infoBox = LinearLayout(ctx)
        infoBox.orientation = LinearLayout.VERTICAL
        infoBox.background = Ui.bg(Ui.CARD, 14, ctx, Ui.BORDER)
        infoBox.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8))
        infoBox.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        (infoBox.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 14)
        root.addView(infoBox)

        antennaBox = LinearLayout(ctx)
        antennaBox.orientation = LinearLayout.VERTICAL
        antennaBox.visibility = View.GONE
        root.addView(antennaBox)

        val pointing = Ui.button(ctx, "ARAH ANTENA — KOMPAS SINYAL")
        pointing.setOnClickListener { act.show(MainActivity.TAB_POINTING) }
        root.addView(pointing)

        val speed = Ui.button(ctx, "SPEED TEST (BUKA DI BROWSER)")
        speed.setOnClickListener {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openspeedtest.com")))
        }
        root.addView(speed)

        val clear = Ui.button(ctx, "HAPUS STATISTIK TRAFFIC")
        clear.setOnClickListener {
            Ui.confirm(act, "Hapus statistik?", "Penghitung traffic sesi dan bulanan di router akan direset ke nol.", "Hapus") {
                Bg.run({ act.app().client().clearTraffic() }) { r ->
                    r.onSuccess { Ui.toast(ctx, "Statistik dihapus") }
                    r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
                }
            }
        }
        root.addView(clear)

        val off = Ui.button(ctx, "MATIKAN ROUTER", primary = false)
        off.setOnClickListener {
            Ui.confirm(act, "Matikan router?", "Router akan MATI TOTAL sampai kamu menyalakannya lagi dengan tombol fisik. Lanjutkan?", "Matikan") {
                Bg.run({ act.app().client().deviceControl(4) }) { r ->
                    r.onSuccess { Ui.toast(ctx, "Perintah matikan terkirim") }
                    r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
                }
            }
        }
        root.addView(off)

        val factory = Ui.button(ctx, "RESET PABRIK", primary = false)
        factory.setOnClickListener {
            Ui.confirm(act, "Reset pabrik?", "SEMUA setelan router (nama WiFi, password WiFi, password admin) kembali ke bawaan pabrik. Ini tidak bisa dibatalkan.", "Lanjut") {
                Ui.confirm(act, "Yakin 100%?", "Router akan restart dengan setelan pabrik dan kamu harus menyetel ulang dari awal.", "Ya, Reset Pabrik") {
                    Bg.run({ act.app().client().deviceControl(2) }) { r ->
                        r.onSuccess { Ui.toast(ctx, "Reset pabrik terkirim — router menyala ulang") }
                        r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
                    }
                }
            }
        }
        root.addView(factory)
        return scroll
    }

    override fun onShow() { imeiRevealed = false; load() }

    private fun infoRow(k: String, v: String): LinearLayout {
        val ctx = act
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9))
        val kt = Ui.text(ctx, k, 14f, Ui.SUB)
        kt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(kt)
        row.addView(Ui.text(ctx, v, 14f, Ui.TXT, true))
        return row
    }

    private fun load() {
        if (loading) return
        loading = true
        Bg.run({
            val c = act.app().client()
            c.ensureSession()
            val hosts = c.hosts()
            val info = c.deviceInfo()
            val boot = c.bootTimeSec()
            val ant = c.antennaTypes()
            val myIp = deviceIpHint()
            Triple(hosts, info, boot) to (ant to myIp)
        }) { res ->
            loading = false
            res.onSuccess { (a, b) ->
                val (hosts, info, boot) = a
                val (ant, _) = b
                subTv.text = "Terhubung ke WiFi router · ${hosts.size} perangkat"
                renderHosts(hosts)
                renderInfo(info, boot)
                renderAntenna(ant)
            }
            res.onFailure { subTv.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun deviceIpHint(): String = ""

    private fun renderHosts(hosts: List<com.netaver.hilink.net.HilinkClient.Host>) {
        val ctx = act
        hostsBox.removeAllViews()
        if (hosts.isEmpty()) {
            val t = Ui.text(ctx, "Daftar perangkat tidak tersedia dari router.", 14f, Ui.SUB)
            t.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
            hostsBox.addView(t)
            return
        }
        hosts.forEachIndexed { i, h ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11))
            row.addView(Ui.text(ctx, "📱 " + h.name.ifEmpty { "Perangkat" }, 15.5f, Ui.TXT, true))
            val dm = Ui.text(ctx, "${h.ip} · ${h.mac}", 12.5f, Ui.SUB)
            dm.setPadding(0, Ui.dp(ctx, 2), 0, 0)
            row.addView(dm)
            hostsBox.addView(row)
            if (i < hosts.size - 1) {
                val div = View(ctx)
                div.setBackgroundColor(Ui.BORDER)
                div.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                hostsBox.addView(div)
            }
        }
    }

    private fun renderInfo(info: com.netaver.hilink.net.HilinkClient.DeviceInfo, bootSec: Long) {
        val ctx = act
        infoBox.removeAllViews()
        infoBox.addView(infoRow("Model", info.name.ifEmpty { "—" }))
        infoBox.addView(infoRow("Firmware", info.sw.ifEmpty { "—" }))
        infoBox.addView(infoRow("Hardware", info.hw.ifEmpty { "—" }))
        imeiFull = info.imei
        val imeiRow = LinearLayout(ctx)
        imeiRow.orientation = LinearLayout.HORIZONTAL
        imeiRow.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9))
        val imeiKt = Ui.text(ctx, "IMEI", 14f, Ui.SUB)
        imeiKt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        imeiRow.addView(imeiKt)
        // Eye button: same pattern as the Ubah IMEI eye (ModWebUi), but with
        // a dark background matching the Devices tab (BORDER, not blue BTN),
        // and a white eye icon. No bare emoji glyph.
        val eyeBox = LinearLayout(ctx)
        eyeBox.orientation = LinearLayout.HORIZONTAL
        eyeBox.background = Ui.bg(Ui.BORDER, 10, ctx)
        eyeBox.gravity = Gravity.CENTER
        eyeBox.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            rightMargin = Ui.dp(ctx, 8)
        }
        val eyeIv = ImageView(ctx)
        eyeIv.setImageDrawable(Ui.EyeDrawable(ctx, false))
        eyeIv.scaleType = ImageView.ScaleType.FIT_CENTER
        // EyeDrawable paints in SUB gray; on the dark button it must be white.
        // ImageView color filter persists across the toggle's drawable swaps.
        eyeIv.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        eyeIv.setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8), 0)
        eyeIv.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 46), ViewGroup.LayoutParams.MATCH_PARENT)
        eyeBox.addView(eyeIv)
        eyeBox.setOnClickListener {
            if (imeiFull.isEmpty()) return@setOnClickListener
            // Drawable set from the pre-toggle value, so it equals the NEW
            // shown state — same as ModWebUi's !curMasked.
            eyeIv.setImageDrawable(Ui.EyeDrawable(ctx, !imeiRevealed))
            imeiRevealed = !imeiRevealed
            imeiValueTv?.text = if (imeiRevealed) imeiFull else Format.maskImei(imeiFull)
        }
        imeiRow.addView(eyeBox)
        val imeiVal = Ui.text(ctx, if (imeiFull.isEmpty()) "—" else if (imeiRevealed) imeiFull else Format.maskImei(imeiFull), 14f, Ui.TXT, true)
        imeiValueTv = imeiVal
        imeiRow.addView(imeiVal)
        infoBox.addView(imeiRow)
        infoBox.addView(infoRow("Serial", info.serial.ifEmpty { "—" }))
        infoBox.addView(infoRow("MAC LAN", info.mac.ifEmpty { "—" }))
        infoBox.addView(infoRow("Nyala sejak", if (bootSec > 0) Format.duration(bootSec) + " lalu" else "—"))
    }

    private fun renderAntenna(ant: Pair<String, String>?) {
        val ctx = act
        antennaBox.removeAllViews()
        if (ant == null) {
            antennaBox.visibility = View.GONE
            return
        }
        antennaBox.visibility = View.VISIBLE
        val card = Ui.card(ctx)
        card.addView(Ui.text(ctx, "ANTENA (khusus router CPE)", 11.5f, Ui.SUB))
        fun label(v: String) = when (v) { "0" -> "Internal"; "1" -> "Eksternal"; else -> "?" }
        val line = Ui.text(ctx, "Antena 1: ${label(ant.first)} · Antena 2: ${label(ant.second)}", 14.5f, Ui.TXT)
        line.setPadding(0, Ui.dp(ctx, 6), 0, 0)
        card.addView(line)
        val btn = Ui.button(ctx, "GANTI KE " + if (ant.first == "0") "EKSTERNAL" else "INTERNAL")
        btn.setOnClickListener {
            val target = if (ant.first == "0") "1" else "0"
            Bg.run({ act.app().client().setAntenna(target, target) }) { r ->
                r.onSuccess { Ui.toast(ctx, "Antena diubah"); load() }
                r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
            }
        }
        card.addView(btn)
        antennaBox.addView(card)
    }
}

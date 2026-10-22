package com.netaver.hilink.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.Bands
import com.netaver.hilink.core.NetModes

/** LTE band lock: read current mask, tick bands, apply. Plus Auto (all bands). */
class BandScreen(private val act: MainActivity) : Screen {
    private lateinit var statusTv: TextView
    private val boxes = mutableMapOf<Int, CheckBox>()
    private var currentMode: String = NetModes.AUTO
    private var loading = false

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        root.addView(Ui.text(ctx, "Kunci Band LTE", 24f, Ui.TXT, true))
        statusTv = Ui.text(ctx, "Memuat dari router…", 13.5f, Ui.SUB)
        statusTv.setPadding(0, Ui.dp(ctx, 3), 0, 0)
        root.addView(statusTv)

        val autoBtn = Ui.button(ctx, "SEMUA BAND (OTOMATIS)")
        autoBtn.layoutParams = (autoBtn.layoutParams as LinearLayout.LayoutParams).apply { topMargin = Ui.dp(ctx, 14) }
        autoBtn.setOnClickListener { apply(NetModes.AUTO, Bands.ALL_HEX, "Semua band (otomatis)") }
        root.addView(autoBtn)

        var lastDuplex = ""
        for (band in Bands.ALL) {
            if (band.duplex != lastDuplex) {
                lastDuplex = band.duplex
                val h = Ui.text(ctx, lastDuplex, 13f, Ui.SUB, true)
                h.setPadding(0, Ui.dp(ctx, 16), 0, Ui.dp(ctx, 8))
                root.addView(h)
            }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.background = Ui.bg(Ui.CARD2, 0, ctx)
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 6), Ui.dp(ctx, 14), Ui.dp(ctx, 6))
            val cb = CheckBox(ctx)
            cb.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
            boxes[band.n] = cb
            row.addView(cb)
            val name = Ui.text(ctx, "Band ${band.n}", 16f, Ui.TXT, true)
            name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(name)
            row.addView(Ui.text(ctx, band.freq, 12.5f, Ui.SUB))
            row.setOnClickListener { cb.isChecked = !cb.isChecked }
            root.addView(row)
            val div = View(ctx)
            div.setBackgroundColor(Ui.BORDER)
            div.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            root.addView(div)
        }

        val note = Ui.text(ctx, "Setelah diterapkan, modem putus-sambung ±10 detik. Daftar band mengikuti kemampuan modem — band yang tidak didukung akan diabaikan router.", 12.5f, Ui.FAINT)
        note.setPadding(0, Ui.dp(ctx, 8), 0, 0)
        root.addView(note)

        val applyBtn = Ui.button(ctx, "TERAPKAN KUNCI BAND")
        applyBtn.setOnClickListener {
            val sel = boxes.filterValues { it.isChecked }.keys
            if (sel.isEmpty()) {
                Ui.toast(ctx, "Pilih minimal 1 band")
                return@setOnClickListener
            }
            apply(currentMode, Bands.maskOf(sel), "Band " + sel.sorted().joinToString(", "))
        }
        root.addView(applyBtn)
        return scroll
    }

    override fun onShow() { load() }

    private fun load() {
        if (loading) return
        loading = true
        Bg.run({
            val c = act.app().client()
            c.ensureSession()
            c.netMode()
        }) { res ->
            loading = false
            res.onSuccess { nm ->
                currentMode = nm.mode.ifEmpty { NetModes.AUTO }
                val sel = Bands.parseMask(nm.lteBand)
                for ((n, cb) in boxes) cb.isChecked = Bands.isAll(nm.lteBand) || sel.contains(n)
                statusTv.text = "Sekarang: " + NetModes.label(currentMode) + " · " +
                    if (Bands.isAll(nm.lteBand)) "semua band" else "band " + sel.sorted().joinToString(", ").ifEmpty { "—" }
            }
            res.onFailure { statusTv.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun apply(mode: String, lteHex: String, label: String) {
        val ctx = act
        Ui.confirm(act, "Terapkan kunci band?", "$label akan diterapkan. Modem akan putus-sambung sebentar.", "Terapkan") {
            Bg.run({ act.app().client().setNetMode(mode, Bands.NETWORK_BAND_ALL, lteHex) }) { r ->
                r.onSuccess {
                    Ui.toast(ctx, "Terkirim: $label — tunggu ±10 detik")
                    statusTv.text = "Diterapkan: $label · memuat ulang…"
                    
                    load()
                }
                r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
            }
        }
    }
}

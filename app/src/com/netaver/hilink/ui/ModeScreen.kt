package com.netaver.hilink.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.netaver.hilink.core.NetModes

/** Network mode (auto / 4G / 3G / 2G), mobile-data switch, operator scan. */
class ModeScreen(private val act: MainActivity) : Screen {
    private lateinit var statusTv: TextView
    private lateinit var scanTv: TextView
    private val radios = mutableMapOf<String, RadioButton>()
    private lateinit var dataSwitch: Switch
    private var loading = false
    private var curLteBand: String = com.netaver.hilink.core.Bands.ALL_HEX
    private var curNetBand: String = com.netaver.hilink.core.Bands.NETWORK_BAND_ALL

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        root.addView(Ui.text(ctx, "Mode Jaringan", 24f, Ui.TXT, true))
        statusTv = Ui.text(ctx, "Memuat dari router…", 13.5f, Ui.SUB)
        statusTv.setPadding(0, Ui.dp(ctx, 3), 0, 0)
        root.addView(statusTv)

        val card = Ui.card(ctx)
        NetModes.OPTIONS.forEachIndexed { i, (code, label) ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8))
            val rb = RadioButton(ctx)
            rb.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
            radios[code] = rb
            row.addView(rb)
            val t = Ui.text(ctx, label, 16.5f, Ui.TXT)
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t)
            row.setOnClickListener { select(code) }
            rb.setOnClickListener { select(code) }
            card.addView(row)
            if (i < NetModes.OPTIONS.size - 1) {
                val div = View(ctx)
                div.setBackgroundColor(Ui.BORDER)
                div.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                card.addView(div)
            }
        }
        root.addView(card)

        val card2 = Ui.card(ctx)
        val swRow = LinearLayout(ctx)
        swRow.orientation = LinearLayout.HORIZONTAL
        val swT = Ui.text(ctx, "Data seluler", 16.5f, Ui.TXT)
        swT.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        swRow.addView(swT)
        dataSwitch = Switch(ctx)
        dataSwitch.isChecked = true
        dataSwitch.setOnCheckedChangeListener { _, on ->
            Bg.run({ act.app().client().setDataSwitch(on) }) { r ->
                r.onSuccess { Ui.toast(ctx, if (on) "Data dinyalakan" else "Data dimatikan") }
                r.onFailure {
                    Ui.toast(ctx, "Gagal: ${it.message}")
                }
            }
        }
        swRow.addView(dataSwitch)
        card2.addView(swRow)
        root.addView(card2)

        val note = Ui.text(ctx, "Mode \"4G saja\" mencegah modem turun ke 3G/2G saat sinyal 4G lemah — koneksi bisa putus total bila 4G hilang.", 12.5f, Ui.FAINT)
        note.setPadding(0, Ui.dp(ctx, 12), 0, 0)
        root.addView(note)

        val applyBtn = Ui.button(ctx, "TERAPKAN MODE")
        applyBtn.setOnClickListener {
            val code = radios.entries.firstOrNull { it.value.isChecked }?.key ?: NetModes.AUTO
            Bg.run({ act.app().client().setNetMode(code, curNetBand, curLteBand) }) { r ->
                r.onSuccess {
                    Ui.toast(ctx, "Mode diterapkan: " + NetModes.label(code))
                    statusTv.text = "Sekarang: " + NetModes.label(code)
                }
                r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
            }
        }
        root.addView(applyBtn)

        val scanBtn = Ui.button(ctx, "PINDAI OPERATOR DI SEKITAR")
        scanBtn.setOnClickListener {
            scanTv.text = "Memindai… (bisa 15–30 detik)"
            Bg.run({ val c = act.app().client(); c.ensureSession(); c.plmnScan() }) { r ->
                r.onSuccess { list ->
                    scanTv.text = if (list.isEmpty()) "Tidak ada hasil pindai dari router."
                    else "Operator terdeteksi:\n" + list.joinToString("\n") { "• $it" }
                }
                r.onFailure { scanTv.text = "Pindai gagal: ${it.message}" }
            }
        }
        root.addView(scanBtn)
        scanTv = Ui.text(ctx, "", 13.5f, Ui.TXT)
        scanTv.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        root.addView(scanTv)
        return scroll
    }

    private fun select(code: String) {
        for ((c, rb) in radios) rb.isChecked = c == code
    }

    override fun onShow() { load() }

    private fun load() {
        if (loading) return
        loading = true
        Bg.run({
            val c = act.app().client()
            c.ensureSession()
            c.netMode() to c.status()
        }) { res ->
            loading = false
            res.onSuccess { (nm, st) ->
                curLteBand = nm.lteBand.ifEmpty { curLteBand }
                curNetBand = nm.networkBand.ifEmpty { curNetBand }
                select(nm.mode.ifEmpty { NetModes.AUTO })
                dataSwitch.setOnCheckedChangeListener(null)
                dataSwitch.isChecked = st.connectionStatus != "902"
                dataSwitch.setOnCheckedChangeListener { _, on ->
                    Bg.run({ act.app().client().setDataSwitch(on) }) { r ->
                        r.onFailure { Ui.toast(act, "Gagal: ${it.message}") }
                    }
                }
                statusTv.text = "Sekarang: " + NetModes.label(nm.mode) +
                    if (st.connectionStatus == "901") " · data terhubung" else " · data tidak terhubung"
            }
            res.onFailure { statusTv.text = "Gagal memuat: ${it.message}" }
        }
    }
}

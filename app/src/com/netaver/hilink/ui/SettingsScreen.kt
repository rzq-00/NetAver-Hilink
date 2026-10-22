package com.netaver.hilink.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** App + router settings hub. Connection/login lives in its own tab (LoginScreen) since v2.2.0. */
class SettingsScreen(private val act: MainActivity) : Screen {
    private val chips = mutableMapOf<Int, TextView>()

    private fun gapView2(h: Int): View {
        val v = View(act)
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, h))
        return v
    }

    private fun navRow(label: String, desc: String, go: () -> Unit): View {
        val ctx = act
        val card = Ui.card(ctx)
        val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(ctx, label, 16f, Ui.TXT, true))
        col.addView(Ui.text(ctx, desc, 12.5f, Ui.SUB))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.text(ctx, "›", 22f, Ui.FAINT))
        card.addView(row)
        card.setOnClickListener { go() }
        return card
    }

    private fun label(s: String): TextView {
        val t = Ui.text(act, s, 12f, Ui.SUB, true)
        t.setPadding(0, 0, 0, Ui.dp(act, 6))
        return t
    }

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        root.addView(Ui.text(ctx, "Setelan", 24f, Ui.TXT, true))
        val sub = Ui.text(ctx, "Pengaturan router & aplikasi", 13.5f, Ui.SUB)
        sub.setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 14))
        root.addView(sub)

        root.addView(Ui.text(ctx, "PENGATURAN ROUTER", 13f, Ui.ACCENT, true))
        val hubSub = Ui.text(ctx, "Tanpa membuka WebUI", 12.5f, Ui.SUB)
        hubSub.setPadding(0, Ui.dp(ctx, 2), 0, Ui.dp(ctx, 10))
        root.addView(hubSub)
        root.addView(navRow("WiFi", "Nama SSID, password, keamanan, sembunyikan SSID") { act.show(MainActivity.TAB_WIFI) })
        root.addView(navRow("LAN & DHCP", "IP router, rentang DHCP, lama sewa") { act.show(MainActivity.TAB_DHCP) })
        root.addView(navRow("USSD", "Kode dial operator (*123# dll.) dari SIM router") { act.show(MainActivity.TAB_USSD) })
        root.addView(navRow("Profil APN", "Daftar profil APN: buat, ubah, jadikan aktif") { act.show(MainActivity.TAB_APN) })
        root.addView(navRow("Diagnostik", "Laporan lengkap: cek semua endpoint + data mentah (ala NetAver DNS)") { act.show(MainActivity.TAB_DIAG) })
        root.addView(gapView2(6))

        val intLabel = label("INTERVAL PERBARUI DASHBOARD")
        intLabel.setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 6))
        root.addView(intLabel)
        val chipRow = LinearLayout(ctx)
        chipRow.orientation = LinearLayout.HORIZONTAL
        for ((secs, txt) in listOf(5 to "5 dtk", 10 to "10 dtk", 30 to "30 dtk", 0 to "Manual")) {
            val c = Ui.chip(ctx, txt, false)
            c.setOnClickListener {
                act.app().prefs.refreshSecs = secs
                renderChips()
            }
            chips[secs] = c
            chipRow.addView(c)
        }
        root.addView(chipRow)

        val version = try {
            act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
        val aboutLabel = label("TENTANG")
        aboutLabel.setPadding(0, Ui.dp(ctx, 18), 0, Ui.dp(ctx, 6))
        root.addView(aboutLabel)
        root.addView(navRow("NetAver HiLink", "Versi $version · manajer router Huawei (HiLink)") {
            Ui.toast(ctx, "NetAver HiLink v$version")
        })
        return scroll
    }

    override fun onShow() {
        renderChips()
    }

    private fun renderChips() {
        val cur = act.app().prefs.refreshSecs
        for ((secs, chip) in chips) {
            val sel = secs == cur
            chip.setTextColor(if (sel) android.graphics.Color.WHITE else android.graphics.Color.rgb(174, 185, 212))
            chip.background = Ui.bg(if (sel) Ui.BTN else android.graphics.Color.rgb(26, 36, 71), 20, act)
        }
    }
}

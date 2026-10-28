package com.netaver.hilink.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** App + router settings hub. Connection/login lives in its own tab (LoginScreen) since v2.2.0. */
class SettingsScreen(private val act: MainActivity) : Screen {
    private val chips = mutableMapOf<Int, TextView>()
    private lateinit var modSection: LinearLayout
    private lateinit var ttlValueView: TextView
    private lateinit var imeiDescView: TextView
    private lateinit var updateResultView: TextView
    private lateinit var updateDownloadBtn: TextView
    private var updateChecking = false
    private var updateDownloadUrl: String? = null
    private var ttlDigits: String? = null
    private var imeiMasked: String? = null
    private var imeiFull: String? = null
    private val modDialogs by lazy { ModDialogs(act) { refreshModSection() } }

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

        // MOD WEBUI (Mod1.21 only): built once, hidden until onShow() proves
        // the mod daemon data is served. On stock firmware it never appears.
        modSection = LinearLayout(ctx)
        modSection.orientation = LinearLayout.VERTICAL
        modSection.visibility = View.GONE
        val modHead = LinearLayout(ctx)
        modHead.orientation = LinearLayout.HORIZONTAL
        modHead.addView(Ui.text(ctx, "MOD WEBUI", 12f, Ui.SUB, true))
        val badge = Ui.text(ctx, "Mod1.21 terdeteksi", 10.5f, Ui.GREEN, true)
        badge.background = Ui.bg(android.graphics.Color.rgb(14, 36, 23), 10, ctx, android.graphics.Color.rgb(30, 70, 48))
        badge.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 3), Ui.dp(ctx, 8), Ui.dp(ctx, 3))
        val badgeLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        badgeLp.leftMargin = Ui.dp(ctx, 8)
        badge.layoutParams = badgeLp
        modHead.addView(badge)
        modSection.addView(modHead)
        val modNote = Ui.text(ctx, "Hanya muncul bila router memakai WebUI Mod1.21. Di firmware stock, bagian ini tidak tampil.", 12f, Ui.SUB)
        modNote.setPadding(0, Ui.dp(ctx, 4), 0, 0)
        modSection.addView(modNote)
        // TTL row: value on the right, as in the mockup.
        val ttlCard = Ui.card(ctx)
        val ttlRow = LinearLayout(ctx); ttlRow.orientation = LinearLayout.HORIZONTAL
        val ttlCol = LinearLayout(ctx); ttlCol.orientation = LinearLayout.VERTICAL
        ttlCol.addView(Ui.text(ctx, "TTL", 16f, Ui.TXT, true))
        ttlCol.addView(Ui.text(ctx, "Nilai TTL paket keluar", 12.5f, Ui.SUB))
        ttlRow.addView(ttlCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ttlValueView = Ui.text(ctx, "—", 15f, Ui.ACCENT, true)
        ttlRow.addView(ttlValueView)
        ttlRow.addView(Ui.text(ctx, "›", 22f, Ui.FAINT))
        ttlCard.addView(ttlRow)
        ttlCard.setOnClickListener { modDialogs.ttlDialog(ttlDigits) }
        modSection.addView(ttlCard)
        val imeiCard = Ui.card(ctx)
        val imeiRow = LinearLayout(ctx); imeiRow.orientation = LinearLayout.HORIZONTAL
        val imeiCol = LinearLayout(ctx); imeiCol.orientation = LinearLayout.VERTICAL
        imeiCol.addView(Ui.text(ctx, "Ubah IMEI", 16f, Ui.TXT, true))
        imeiDescView = Ui.text(ctx, "IMEI saat ini: —", 12.5f, Ui.SUB)
        imeiCol.addView(imeiDescView)
        imeiRow.addView(imeiCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        imeiRow.addView(Ui.text(ctx, "›", 22f, Ui.FAINT))
        imeiCard.addView(imeiRow)
        imeiCard.setOnClickListener { modDialogs.imeiDialog(imeiMasked, imeiFull, "") }
        modSection.addView(imeiCard)
        modSection.addView(navRow("Generate IMEI", "Dari IMEI existing — perangkat & model tetap sama") {
            modDialogs.generateDialog { generated -> modDialogs.imeiDialog(imeiMasked, imeiFull, generated) }
        })
        root.addView(modSection)

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

        // PEMBARUAN: MANUAL update check against the public GitHub Releases
        // API — no token, no auto-check, no background check, no in-app
        // install. VERSION NAME ONLY: this UI shows and compares the release
        // version name (e.g. 2.3.1), never any internal build number.
        val updLabel = label("PEMBARUAN")
        updLabel.setPadding(0, Ui.dp(ctx, 18), 0, Ui.dp(ctx, 6))
        root.addView(updLabel)
        val updCard = Ui.card(ctx)
        val curRow = LinearLayout(ctx); curRow.orientation = LinearLayout.HORIZONTAL
        curRow.addView(Ui.text(ctx, "Versi saat ini", 16f, Ui.TXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        curRow.addView(Ui.text(ctx, version, 15f, Ui.TXT, true))
        updCard.addView(curRow)
        val cekRow = LinearLayout(ctx); cekRow.orientation = LinearLayout.HORIZONTAL
        cekRow.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        cekRow.addView(Ui.text(ctx, "Cek Pembaruan", 16f, Ui.TXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val cekBtn = Ui.text(ctx, "Cek", 14f, android.graphics.Color.WHITE, true)
        cekBtn.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 8), Ui.dp(ctx, 18), Ui.dp(ctx, 8))
        cekBtn.background = Ui.bg(Ui.BTN, 10, ctx)
        cekBtn.setOnClickListener { checkUpdate() }
        cekRow.addView(cekBtn)
        updCard.addView(cekRow)
        updateResultView = Ui.text(ctx, "", 13f, Ui.SUB)
        updateResultView.setPadding(0, Ui.dp(ctx, 10), 0, 0)
        updCard.addView(updateResultView)
        updateDownloadBtn = Ui.text(ctx, "Unduh", 14f, android.graphics.Color.WHITE, true)
        updateDownloadBtn.gravity = android.view.Gravity.CENTER
        updateDownloadBtn.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12))
        updateDownloadBtn.background = Ui.bg(Ui.BTN, 10, ctx)
        updateDownloadBtn.visibility = View.GONE
        updateDownloadBtn.setOnClickListener {
            val url = updateDownloadUrl ?: return@setOnClickListener
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Ui.toast(ctx, "Tidak dapat membuka browser")
            }
        }
        updCard.addView(updateDownloadBtn)
        root.addView(updCard)
        return scroll
    }

    /** Manual update check (tap only). Fetches the public releases list with
     *  no auth, compares the latest release's VERSION NAME against this
     *  app's version name, and either offers the release page in the
     *  browser or reports up-to-date. Network/API failure toasts and leaves
     *  the previous state untouched; nothing is ever claimed on failure. */
    private fun checkUpdate() {
        if (updateChecking) return
        updateChecking = true
        updateDownloadUrl = null
        updateDownloadBtn.visibility = View.GONE
        updateResultView.text = "Memeriksa…"
        updateResultView.setTextColor(Ui.SUB)
        Bg.run({
            val conn = java.net.URL("https://api.github.com/repos/rzq-00/NetAver-Hilink/releases")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NetAver-HiLink")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            if (conn.responseCode != 200) throw Exception("GitHub: HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = org.json.JSONArray(body)
            if (arr.length() == 0) throw Exception("Tidak ada rilis")
            val rel = arr.getJSONObject(0)
            Triple(rel.optString("tag_name", ""), rel.optString("body", ""), rel.optString("html_url", ""))
        }) { res ->
            updateChecking = false
            res.onSuccess { (tag, notes, url) ->
                val latest = tag.removePrefix("v").trim()
                val cmp = if (latest.isEmpty()) null else compareVersion(latest, currentVersionName())
                when {
                    cmp == null -> {
                        updateResultView.text = "Versi terbaru: $tag\n${shortNotes(notes)}"
                        updateResultView.setTextColor(Ui.TXT)
                        showDownload(url)
                    }
                    cmp > 0 -> {
                        updateResultView.text = "Versi terbaru: $latest\n${shortNotes(notes)}"
                        updateResultView.setTextColor(Ui.TXT)
                        showDownload(url)
                    }
                    else -> {
                        updateResultView.text = "✓ Sudah versi terbaru"
                        updateResultView.setTextColor(Ui.GREEN)
                    }
                }
            }.onFailure { e ->
                updateResultView.text = ""
                Ui.toast(act, "Gagal memeriksa pembaruan: ${e.message}")
            }
        }
    }

    private fun showDownload(url: String) {
        if (url.isEmpty()) return
        updateDownloadUrl = url
        updateDownloadBtn.visibility = View.VISIBLE
    }

    private fun currentVersionName(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "2.3.1"
    } catch (e: Exception) {
        "2.3.1"
    }

    /** Semantic version-name compare (split on '.', numeric per segment).
     *  Returns >0 if a > b, 0 if equal, <0 if a < b. No build number involved. */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".")
        val pb = b.split(".")
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun shortNotes(body: String): String =
        body.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString("\n")

    override fun onShow() {
        renderChips()
        refreshModSection()
    }

    /** Detect Mod1.21 (via the same add_param data the band feature uses) and,
     *  when present, refresh the TTL value and the masked current IMEI. Any
     *  failure hides the section again — it is optional, never fatal. */
    private fun refreshModSection() {
        Bg.run(
            {
                val client = act.app().client()
                if (!client.modDetected()) return@run Triple(false, null, null)
                val ttl = try {
                    client.modTtl()
                } catch (e: Exception) {
                    null
                }
                var full: String? = null
                val masked = try {
                    val imei = client.deviceInfo().imei
                    full = imei
                    com.netaver.hilink.core.ImeiKit.maskImei(imei)
                } catch (e: Exception) {
                    null
                }
                // Stored alongside the masked value so the Ubah dialog's eye
                // and SALIN can use the real IMEI (never the masked text).
                imeiFull = full
                Triple(true, ttl, masked)
            },
            { res ->
                res.onSuccess { (detected, ttl, masked) ->
                    if (!detected) {
                        modSection.visibility = View.GONE
                        return@onSuccess
                    }
                    modSection.visibility = View.VISIBLE
                    ttlDigits = ttl
                    ttlValueView.text = when {
                        ttl.isNullOrEmpty() || ttl == "0" -> "Nonaktif"
                        else -> ttl
                    }
                    if (masked != null) imeiMasked = masked
                    imeiDescView.text = if (masked != null) {
                        "IMEI saat ini: $masked"
                    } else {
                        "IMEI saat ini: —"
                    }
                }.onFailure {
                    modSection.visibility = View.GONE
                }
            }
        )
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

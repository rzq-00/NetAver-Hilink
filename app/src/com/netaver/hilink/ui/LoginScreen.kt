package com.netaver.hilink.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.LanGuard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Connection + session: router address, credentials (LAN-guarded, Keystore-encrypted), login state. */
class LoginScreen(private val act: MainActivity) : Screen {
    private lateinit var ipField: EditText
    private lateinit var userField: EditText
    private lateinit var passField: EditText
    private lateinit var sessionCard: LinearLayout
    private lateinit var sessionTitle: TextView
    private lateinit var sessionSub: TextView
    private lateinit var statusTv: TextView

    private fun field(hint: String): EditText {
        val e = EditText(act)
        e.hint = hint
        e.setTextColor(Ui.TXT)
        e.setHintTextColor(Ui.FAINT)
        e.textSize = 15.5f
        e.background = Ui.bg(android.graphics.Color.rgb(13, 20, 36), 10, act, Ui.BORDER)
        e.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = Ui.dp(act, 12)
        e.layoutParams = lp
        return e
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

        root.addView(Ui.text(ctx, "Login", 24f, Ui.TXT, true))
        val sub = Ui.text(ctx, "Koneksi ke router", 13.5f, Ui.SUB)
        sub.setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 14))
        root.addView(sub)

        root.addView(label("ALAMAT ROUTER"))
        ipField = field("192.168.8.1")
        root.addView(ipField)
        root.addView(label("USERNAME"))
        userField = field("admin")
        root.addView(userField)
        root.addView(label("PASSWORD"))
        val (passBox, passEdit) = Ui.passwordField(act, "••••••••")
        passField = passEdit
        root.addView(passBox)

        sessionCard = Ui.card(ctx)
        sessionCard.background = Ui.bg(android.graphics.Color.rgb(14, 31, 26), 14, ctx, android.graphics.Color.rgb(30, 70, 51))
        sessionTitle = Ui.text(ctx, "Sesi: —", 15f, Ui.GREEN, true)
        sessionCard.addView(sessionTitle)
        sessionSub = Ui.text(ctx, "Satu sesi dipakai bersama semua layar, diperbarui otomatis hanya bila kedaluwarsa. Password tersimpan terenkripsi (Keystore) di HP.", 13f, android.graphics.Color.rgb(167, 196, 181))
        sessionSub.setPadding(0, Ui.dp(ctx, 4), 0, 0)
        sessionCard.addView(sessionSub)
        root.addView(sessionCard)

        val loginRow = LinearLayout(ctx)
        loginRow.orientation = LinearLayout.HORIZONTAL
        val loginBtn = Ui.button(ctx, "LOGIN")
        loginBtn.layoutParams = (loginBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0; weight = 1f; topMargin = Ui.dp(ctx, 12)
        }
        loginBtn.setOnClickListener {
            Bg.run({ act.app().client().login(true); act.app().client().loginAtMs }) { r ->
                r.onSuccess { renderSession() }
                r.onFailure { Ui.toast(ctx, "Login gagal: ${it.message}"); renderSession() }
            }
        }
        loginRow.addView(loginBtn)
        val logoutBtn = Ui.button(ctx, "LOGOUT")
        logoutBtn.layoutParams = (logoutBtn.layoutParams as LinearLayout.LayoutParams).apply {
            width = 0; weight = 1f; topMargin = Ui.dp(ctx, 12); leftMargin = Ui.dp(ctx, 10)
        }
        logoutBtn.setOnClickListener {
            Bg.run({ act.app().client().logout() }) {
                act.app().resetClient()
                Ui.toast(ctx, "Logout — sesi di router ditutup")
                renderSession()
            }
        }
        loginRow.addView(logoutBtn)
        root.addView(loginRow)

        statusTv = Ui.text(ctx, "", 13.5f, Ui.SUB)
        statusTv.setPadding(0, Ui.dp(ctx, 6), 0, 0)
        root.addView(statusTv)

        val save = Ui.button(ctx, "SIMPAN & TES KONEKSI")
        save.setOnClickListener { saveAndTest() }
        root.addView(save)

        val webui = Ui.button(ctx, "BUKA WEBUI ROUTER")
        webui.setOnClickListener {
            when (val g = LanGuard.check(act.app().prefs.routerIp)) {
                is LanGuard.Result.Ok ->
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://" + g.host)))
                is LanGuard.Result.Bad -> Ui.toast(act, g.reason)
            }
        }
        root.addView(webui)

        val note = Ui.text(ctx, "Keamanan: app ini hanya mau bicara ke alamat LAN (10.x / 172.16–31.x / 192.168.x) agar password router tidak pernah terkirim ke server publik. Tanpa WebView, tanpa server perantara — HP langsung ke routermu.", 12.5f, Ui.FAINT)
        note.setPadding(0, Ui.dp(ctx, 14), 0, 0)
        root.addView(note)
        return scroll
    }

    override fun onShow() {
        val p = act.app().prefs
        ipField.setText(p.routerIp)
        userField.setText(p.username)
        passField.setText(p.password())
        renderSession()
    }

    private fun renderSession() {
        val c = act.app().client()
        if (c.loggedIn) {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(c.loginAtMs))
            sessionTitle.text = "● Sesi aktif"
            sessionTitle.setTextColor(Ui.GREEN)
            sessionSub.text = "Login $t · satu sesi dipakai bersama semua layar, diperbarui otomatis hanya bila kedaluwarsa. Password tersimpan terenkripsi (Keystore) di HP."
        } else {
            sessionTitle.text = "○ Belum login"
            sessionTitle.setTextColor(Ui.SUB)
            sessionSub.text = "App akan login sekali saat pertama membuka data yang butuh sesi. Password tersimpan terenkripsi (Keystore) di HP."
        }
    }

    private fun saveAndTest() {
        val ctx = act
        val checked = LanGuard.check(ipField.text.toString())
        val host = when (checked) {
            is LanGuard.Result.Ok -> checked.host
            is LanGuard.Result.Bad -> {
                Ui.toast(ctx, checked.reason)
                statusTv.text = checked.reason
                return
            }
        }
        val p = act.app().prefs
        p.routerIp = host
        p.username = userField.text.toString().trim().ifEmpty { "admin" }
        val encOk = p.setPassword(passField.text.toString())
        p.loginScheme = -1 // credentials changed: re-learn the scheme
        act.app().resetClient()
        statusTv.text = "Menguji koneksi ke $host…"
        Bg.run({ act.app().client().testConnection() }) { r ->
            r.onSuccess { reach ->
                statusTv.text = if (reach.ok) {
                    "✅ Tersambung ke $host. " + reach.note +
                        if (!encOk) " Catatan: enkripsi HP tidak tersedia — password hanya disimpan di ingatan selama app terbuka." else ""
                } else {
                    "❌ " + reach.note
                }
                renderSession()
            }
            r.onFailure { statusTv.text = "❌ Gagal: ${it.message}" }
        }
    }
}

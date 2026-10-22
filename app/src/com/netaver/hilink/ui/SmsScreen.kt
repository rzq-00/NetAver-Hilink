package com.netaver.hilink.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.net.HilinkClient

/** Native SMS: inbox list, read/reply/delete, compose. */
class SmsScreen(private val act: MainActivity) : Screen {
    private lateinit var listBox: LinearLayout
    private lateinit var subTv: TextView
    private var loading = false

    override fun build(actA: Activity): View {
        val ctx = act
        val scroll = ScrollView(ctx)
        scroll.setBackgroundColor(Ui.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        scroll.addView(root)

        val head = LinearLayout(ctx); head.orientation = LinearLayout.HORIZONTAL
        val titleBox = LinearLayout(ctx); titleBox.orientation = LinearLayout.VERTICAL
        titleBox.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        titleBox.addView(Ui.text(ctx, "SMS", 24f, Ui.TXT, true))
        subTv = Ui.text(ctx, "Memuat…", 13.5f, Ui.SUB)
        titleBox.addView(subTv)
        head.addView(titleBox)
        val fab = Ui.text(ctx, "+", 27f, Color.WHITE, true)
        fab.gravity = Gravity.CENTER
        fab.background = Ui.bg(Ui.BTN, 27, ctx)
        fab.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 54), Ui.dp(ctx, 54))
        fab.setOnClickListener { compose("") }
        head.addView(fab)
        root.addView(head)

        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.background = Ui.bg(Ui.CARD2, 12, ctx, Ui.BORDER)
        listBox.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        (listBox.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 14)
        root.addView(listBox)

        val refresh = Ui.button(ctx, "MUAT ULANG")
        refresh.setOnClickListener { load() }
        root.addView(refresh)
        return scroll
    }

    override fun onShow() { load() }

    private fun load() {
        if (loading) return
        loading = true
        Bg.run({
            val c = act.app().client()
            c.ensureSession()
            c.smsList(1, 20)
        }) { res ->
            loading = false
            res.onSuccess { list -> render(list) }
            res.onFailure { subTv.text = "Gagal memuat: ${it.message}" }
        }
    }

    private fun render(list: List<HilinkClient.Sms>) {
        val ctx = act
        val unread = list.count { it.read == "0" }
        subTv.text = "Kotak masuk · ${list.size} pesan" + if (unread > 0) " · $unread belum dibaca" else ""
        listBox.removeAllViews()
        if (list.isEmpty()) {
            val t = Ui.text(ctx, "Tidak ada SMS.", 14f, Ui.SUB)
            t.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
            listBox.addView(t)
            return
        }
        list.forEachIndexed { i, sms ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 13), Ui.dp(ctx, 14), Ui.dp(ctx, 13))
            val av = Ui.text(ctx, sms.phone.take(3).ifEmpty { "?" }, 14f, Ui.ACCENT, true)
            av.gravity = Gravity.CENTER
            av.background = Ui.bg(Color.rgb(26, 36, 71), 22, ctx)
            av.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44))
            row.addView(av)
            val mid = LinearLayout(ctx); mid.orientation = LinearLayout.VERTICAL
            mid.setPadding(Ui.dp(ctx, 11), 0, 0, 0)
            mid.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val title = Ui.text(ctx, sms.phone + if (sms.read == "0") "  ●" else "", 15.5f, Ui.TXT, true)
            title.setTextColor(if (sms.read == "0") Ui.ACCENT else Ui.TXT)
            mid.addView(title)
            mid.addView(Ui.text(ctx, sms.content.replace("\n", " ").take(90), 13f, Ui.SUB))
            row.addView(mid)
            val date = Ui.text(ctx, sms.date.substringBefore(" ").ifEmpty { sms.date }, 11.5f, Ui.FAINT)
            row.addView(date)
            row.setOnClickListener { openSms(sms) }
            listBox.addView(row)
            if (i < list.size - 1) {
                val div = View(ctx)
                div.setBackgroundColor(Ui.BORDER)
                div.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                listBox.addView(div)
            }
        }
    }

    private fun openSms(sms: HilinkClient.Sms) {
        val ctx = act
        if (sms.read == "0") {
            Bg.run({ act.app().client().smsMarkRead(sms.index) }) { load() }
        }
        // Selectable message text (long-press to select/copy parts, e.g. OTP codes)
        val body = TextView(ctx)
        body.text = sms.content + "\n\n" + sms.date
        body.textSize = 15f
        body.setTextColor(Ui.TXT)
        body.setTextIsSelectable(true)
        body.setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 8), Ui.dp(ctx, 24), 0)
        AlertDialog.Builder(ctx)
            .setTitle(sms.phone)
            .setView(body)
            .setNegativeButton("Hapus") { _, _ ->
                Bg.run({ act.app().client().smsDelete(sms.index) }) { r ->
                    r.onSuccess { Ui.toast(ctx, "SMS dihapus"); load() }
                    r.onFailure { Ui.toast(ctx, "Gagal: ${it.message}") }
                }
            }
            .setNeutralButton("Balas") { _, _ -> compose(sms.phone) }
            .setPositiveButton("Salin") { _, _ ->
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("SMS", sms.content))
                Ui.toast(ctx, "Isi SMS disalin")
            }
            .show()
    }

    private fun compose(prefillPhone: String) {
        val ctx = act
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 8), Ui.dp(ctx, 20), 0)
        val phone = EditText(ctx)
        phone.hint = "Nomor tujuan"
        phone.setText(prefillPhone)
        box.addView(phone)
        val msg = EditText(ctx)
        msg.hint = "Isi pesan"
        msg.minLines = 3
        box.addView(msg)
        AlertDialog.Builder(ctx)
            .setTitle("SMS baru")
            .setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Kirim") { _, _ ->
                val p = phone.text.toString().trim()
                val m = msg.text.toString()
                if (p.isEmpty() || m.isEmpty()) {
                    Ui.toast(ctx, "Nomor dan pesan wajib diisi")
                    return@setPositiveButton
                }
                Bg.run({ act.app().client().smsSend(p, m) }) { r ->
                    r.onSuccess { Ui.toast(ctx, "SMS terkirim"); load() }
                    r.onFailure { Ui.toast(ctx, "Gagal kirim: ${it.message}") }
                }
            }
            .show()
    }
}

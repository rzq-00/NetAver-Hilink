package com.netaver.hilink.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.netaver.hilink.core.ImeiKit
import com.netaver.hilink.net.HilinkClient

/**
 * MOD WEBUI dialogs (Mod1.21 only, shown by SettingsScreen when detected).
 * TTL and IMEI go through the modem's own mod CGI on port 5080 — the same
 * endpoints the WebUI panel uses. Nothing here is a telnet/ADB client and
 * no firmware-store endpoint is invented: HiLink sets the WebUI value only.
 */
class ModDialogs(private val act: MainActivity, private val onRefresh: () -> Unit) {
    private fun col(): LinearLayout {
        val l = LinearLayout(act)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4))
        l.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return l
    }

    private fun scrollWrap(v: View): ScrollView {
        val s = ScrollView(act)
        s.addView(v)
        return s
    }

    private fun lab(s: String): TextView {
        val t = Ui.text(act, s, 11f, Ui.SUB, true)
        t.setPadding(0, Ui.dp(act, 12), 0, Ui.dp(act, 6))
        return t
    }

    private fun fieldStyle(v: View) {
        v.background = Ui.bg(android.graphics.Color.rgb(13, 20, 36), 10, act, Ui.BORDER)
        v.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
    }

    private fun numEdit(hint: String, maxLen: Int): EditText {
        val e = EditText(act)
        e.hint = hint
        e.setTextColor(Ui.TXT); e.setHintTextColor(Ui.FAINT); e.textSize = 15.5f
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.filters = arrayOf(InputFilter.LengthFilter(maxLen))
        e.background = null
        fieldStyle(e)
        return e
    }

    private fun ghostButton(label: String): TextView {
        val t = Ui.text(act, label, 14f, android.graphics.Color.rgb(174, 185, 212), true)
        t.gravity = Gravity.CENTER
        t.background = Ui.bg(android.graphics.Color.rgb(13, 20, 36), 10, act, Ui.BORDER)
        t.setPadding(0, Ui.dp(act, 13), 0, Ui.dp(act, 13))
        return t
    }

    /** Single-select chip group, wrapped into rows of two (as in the mockup).
     *  Returns the row container and a getter for the selected index. */
    private fun chipGroup(labels: List<String>, initial: Int): Pair<LinearLayout, () -> Int> {
        var sel = initial
        val chips = mutableListOf<TextView>()
        val wrap = LinearLayout(act)
        wrap.orientation = LinearLayout.VERTICAL
        for (chunk in labels.chunked(2)) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (lab in chunk) {
                val idx = labels.indexOf(lab)
                val c = Ui.chip(act, lab, idx == initial)
                c.setOnClickListener {
                    sel = idx
                    for ((i, ch) in chips.withIndex()) {
                        val on = i == sel
                        ch.setTextColor(if (on) android.graphics.Color.WHITE else android.graphics.Color.rgb(174, 185, 212))
                        ch.background = Ui.bg(if (on) Ui.BTN else android.graphics.Color.rgb(26, 36, 71), 20, act)
                    }
                }
                chips.add(c)
                row.addView(c)
            }
            wrap.addView(row)
        }
        return Pair(wrap, { sel })
    }

    private fun watcher(onChange: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
        }
    }

    // ---------------- TTL ----------------

    fun ttlDialog(currentDigits: String?) {
        val root = col()
        root.addView(lab("NILAI TTL"))
        val edit = numEdit("0–255 (0 = nonaktif)", 3)
        if (currentDigits != null && currentDigits.all { it.isDigit() }) edit.setText(currentDigits)
        root.addView(edit)
        val note = Ui.text(act, "Nilai diteruskan apa adanya; modem menafsirkan 1 sebagai 64.", 12f, Ui.SUB)
        note.setPadding(0, Ui.dp(act, 8), 0, 0)
        root.addView(note)
        val dlg = AlertDialog.Builder(act)
            .setTitle("TTL")
            .setView(scrollWrap(root))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val v = edit.text.toString()
            val n = v.toIntOrNull()
            if (v.isEmpty() || n == null || n < 0 || n > 255) {
                Ui.toast(act, "Masukkan angka 0–255")
                return@setOnClickListener
            }
            dlg.dismiss()
            Bg.run(
                { act.app().client().modSetTtl(v) },
                { res ->
                    res.onSuccess {
                        onRefresh()
                        Ui.toast(act, "TTL disimpan: $v")
                    }.onFailure { e ->
                        Ui.toast(act, "TTL gagal: ${e.message}")
                    }
                }
            )
        }
    }

    // ---------------- Ubah IMEI ----------------

    fun imeiDialog(currentMasked: String?, currentFull: String?, prefillNew: String) {
        val root = col()
        val warnBox = LinearLayout(act)
        warnBox.orientation = LinearLayout.VERTICAL
        warnBox.background = Ui.bg(android.graphics.Color.rgb(36, 26, 8), 12, act, android.graphics.Color.rgb(90, 66, 16))
        warnBox.setPadding(Ui.dp(act, 12), Ui.dp(act, 11), Ui.dp(act, 12), Ui.dp(act, 11))
        val warn = Ui.text(act, "IMEI salah bisa membuat modem ditolak jaringan. Pastikan 15 digit benar sebelum menyimpan.", 12.5f, Ui.AMBER)
        warnBox.addView(warn)
        root.addView(warnBox)
        val sub = Ui.text(act, "Perangkat sendiri saja — catat IMEI lama sebelum mengubah.", 12.5f, Ui.SUB)
        sub.setPadding(0, Ui.dp(act, 8), 0, 0)
        root.addView(sub)

        root.addView(lab("IMEI SAAT INI"))
        val curRow = LinearLayout(act)
        curRow.orientation = LinearLayout.HORIZONTAL
        val cur = Ui.text(act, currentMasked ?: "—", 15.5f, Ui.SUB)
        fieldStyle(cur)
        cur.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        curRow.addView(cur)
        // Eye toggle on the current IMEI: default MASKED (as before); tap
        // reveals the full IMEI, tap again masks it. Only the display
        // changes — SALIN always copies the full value, never masked text.
        var curMasked = true
        // Eye button: same full row height as the field and the SALIN pill
        // (MATCH_PARENT on the box, not a smaller fixed size), same radius
        // (10) and blue background as SALIN, white centered eye icon.
        val curEyeBox = LinearLayout(act)
        curEyeBox.orientation = LinearLayout.HORIZONTAL
        curEyeBox.background = Ui.bg(Ui.BTN, 10, act)
        curEyeBox.gravity = Gravity.CENTER
        curEyeBox.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            leftMargin = Ui.dp(act, 8)
        }
        val curEye = ImageView(act)
        curEye.setImageDrawable(Ui.EyeDrawable(act, false))
        curEye.scaleType = ImageView.ScaleType.FIT_CENTER
        // EyeDrawable paints in SUB gray; on the blue button it must be white.
        // ImageView color filter persists across the toggle's drawable swaps.
        curEye.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        curEye.setPadding(Ui.dp(act, 11), 0, Ui.dp(act, 11), 0)
        curEye.layoutParams = LinearLayout.LayoutParams(Ui.dp(act, 46), ViewGroup.LayoutParams.MATCH_PARENT)
        curEyeBox.addView(curEye)
        curEyeBox.setOnClickListener {
            curMasked = !curMasked
            cur.text = if (curMasked) currentMasked ?: "—" else currentFull ?: "—"
            curEye.setImageDrawable(Ui.EyeDrawable(act, !curMasked))
        }
        curRow.addView(curEyeBox)
        val copyCur = Ui.text(act, "SALIN", 11f, android.graphics.Color.WHITE, true)
        copyCur.background = Ui.bg(Ui.BTN, 10, act)
        copyCur.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        copyCur.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(act, 8)
        }
        copyCur.setOnClickListener {
            val full = currentFull
            if (full.isNullOrEmpty()) {
                Ui.toast(act, "IMEI saat ini tidak tersedia")
            } else {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("IMEI", full))
                Ui.toast(act, "IMEI saat ini disalin")
            }
        }
        curRow.addView(copyCur)
        root.addView(curRow)

        root.addView(lab("IMEI BARU"))
        val editRow = LinearLayout(act)
        editRow.orientation = LinearLayout.HORIZONTAL
        val edit = numEdit("Masukkan 15 digit IMEI", 15)
        edit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (prefillNew.isNotEmpty()) edit.setText(prefillNew)
        editRow.addView(edit)
        val pasteNew = Ui.text(act, "TEMPEL", 11f, android.graphics.Color.WHITE, true)
        pasteNew.background = Ui.bg(Ui.BTN, 10, act)
        pasteNew.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        pasteNew.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(act, 8)
        }
        pasteNew.setOnClickListener {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val raw = cm?.primaryClip?.getItemAt(0)?.coerceToText(act)?.toString() ?: ""
            val digits = raw.filter { it.isDigit() }
            // Only a fully valid IMEI candidate fills the field; anything else
            // leaves it unchanged so Simpan can never be enabled on bad input.
            if (!ImeiKit.isValidImei(digits)) {
                Ui.toast(act, "Clipboard bukan IMEI 15 digit yang valid")
            } else {
                edit.setText(digits)
                Ui.toast(act, "IMEI ditempel")
            }
        }
        editRow.addView(pasteNew)
        root.addView(editRow)

        val dlg = AlertDialog.Builder(act)
            .setTitle("Ubah IMEI")
            .setView(scrollWrap(root))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dlg.show()
        // Permanent via NV: every IMEI change writes nvbackup into the
        // modem's live user_config.xml before the IMEI is set. If no live
        // config exists, abort — the modem would silently fall back to
        // its package default (none), which would not be permanent.
        fun applyImeiChange(newImei: String) {
            Bg.run(
                {
                    val client = act.app().client()
                    val live: String = try {
                        client.modUserConfig()
                    } catch (e: HilinkClient.HilinkException) {
                        if (e.code != "MODCFG") throw e
                        throw HilinkException2("Konfigurasi mod belum tersimpan di perangkat. Buka panel Mod WebUI sekali dan simpan, lalu coba lagi.")
                    }
                    val updated = live.replace(
                        Regex("<backup_imei>[^<]*</backup_imei>"),
                        "<backup_imei>nvbackup</backup_imei>"
                    )
                    client.modUserConfigSave(updated)
                    client.modSetImei(newImei)
                    client.modApply()
                },
                { res ->
                    res.onSuccess {
                        showWaitingAndRelogin { ok, msg ->
                            Ui.toast(act, msg)
                            if (ok) onRefresh()
                        }
                    }.onFailure { e ->
                        Ui.toast(act, "Ubah IMEI gagal: ${e.message}")
                    }
                }
            )
        }
        val saveBtn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        saveBtn.isEnabled = ImeiKit.isValidImei(prefillNew)
        edit.addTextChangedListener(watcher { s -> saveBtn.isEnabled = ImeiKit.isValidImei(s) })
        saveBtn.setOnClickListener {
            val newImei = edit.text.toString()
            if (!ImeiKit.isValidImei(newImei)) {
                Ui.toast(act, "IMEI baru harus 15 digit dan lolos cek digit")
                return@setOnClickListener
            }
            // Confirmation first: the change is permanent. Batal dismisses
            // only this confirmation, leaving the Ubah IMEI dialog open.
            val msgText = "Ganti IMEI ini permanen,\nAnda Yakin??"
            val span = SpannableString(msgText)
            span.setSpan(
                StyleSpan(Typeface.ITALIC), 0, msgText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            span.setSpan(
                ForegroundColorSpan(Ui.AMBER), 0, msgText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val confirm = AlertDialog.Builder(act)
                .setMessage(span)
                .setNegativeButton("Batal", null)
                .setPositiveButton("OK", null)
                .create()
            confirm.show()
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                confirm.dismiss()
                dlg.dismiss()
                applyImeiChange(newImei)
            }
        }
    }

    private class HilinkException2(msg: String) : Exception(msg)

    /** Waiting dialog + poll for the modem's return + re-login with stored
     *  credentials. Runs on its own thread so the shared Bg executor stays free. */
    private fun showWaitingAndRelogin(onDone: (Boolean, String) -> Unit) {
        val view = LinearLayout(act)
        view.orientation = LinearLayout.VERTICAL
        view.setPadding(Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 16))
        view.addView(ProgressBar(act).apply { isIndeterminate = true })
        val t1 = Ui.text(act, "Modem me-restart…", 15f, Ui.TXT, true)
        t1.setPadding(0, Ui.dp(act, 12), 0, 0)
        view.addView(t1)
        view.addView(Ui.text(act, "Menunggu modem kembali, lalu login ulang otomatis.", 12.5f, Ui.SUB))
        val dlg = AlertDialog.Builder(act).setView(view).setCancelable(false).create()
        dlg.show()
        Thread({
            var ok = false
            var msg = "Modem belum kembali."
            var attempts = 0
            while (attempts < 30 && !ok) {
                attempts++
                try {
                    val client = act.app().client()
                    if (client.testConnection().ok) {
                        try {
                            client.login(manual = true)
                            ok = true
                            msg = "IMEI diubah, modem kembali online."
                        } catch (e: HilinkClient.HilinkException) {
                            if (e.code != "108007") {
                                msg = "Login ulang gagal: ${e.message}"
                                break
                            }
                            // Router lockout: keep waiting for the window to pass.
                        }
                    }
                } catch (e: IllegalStateException) {
                    msg = e.message ?: "Alamat router tidak valid."
                    break
                } catch (e: Exception) {
                    // Router still down: keep waiting.
                }
                if (!ok && attempts < 30) {
                    try {
                        Thread.sleep(5000)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
            if (!ok && msg == "Modem belum kembali.") {
                msg = "Modem belum kembali setelah 150 detik. Buka tab Login dan login ulang manual."
            }
            act.runOnUiThread {
                try {
                    dlg.dismiss()
                } catch (e: Exception) {
                }
                onDone(ok, msg)
            }
        }, "hilink-mod-wait").apply { isDaemon = true; start() }
    }

    // ---------------- Generate IMEI ----------------

    fun generateDialog(onUse: (String) -> Unit) {
        val root = col()
        root.addView(lab("IMEI EXISTING"))
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        val edit = numEdit("Tempel 15 digit IMEI existing", 15)
        edit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(edit)

        val copyExisting = Ui.text(act, "SALIN", 11f, android.graphics.Color.WHITE, true)
        copyExisting.background = Ui.bg(Ui.BTN, 10, act)
        copyExisting.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        copyExisting.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(act, 8)
        }
        copyExisting.setOnClickListener {
            val v = edit.text.toString()
            if (v.isEmpty()) {
                Ui.toast(act, "IMEI existing kosong")
            } else {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("IMEI", v))
                Ui.toast(act, "IMEI existing disalin")
            }
        }
        row.addView(copyExisting)
        val paste = Ui.text(act, "TEMPEL", 11f, android.graphics.Color.WHITE, true)
        paste.background = Ui.bg(Ui.BTN, 10, act)
        paste.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        paste.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(act, 8)
        }
        paste.setOnClickListener {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val raw = cm?.primaryClip?.getItemAt(0)?.coerceToText(act)?.toString() ?: ""
            val digits = raw.filter { it.isDigit() }
            if (digits.isEmpty()) {
                Ui.toast(act, "Clipboard kosong atau bukan angka")
            } else {
                edit.setText(digits)
                Ui.toast(act, "IMEI ditempel")
            }
        }
        row.addView(paste)
        root.addView(row)

        val genBtn = Ui.button(act, "GENERATE")
        root.addView(genBtn)

        val resultBox = LinearLayout(act)
        resultBox.orientation = LinearLayout.VERTICAL
        resultBox.visibility = View.GONE
        resultBox.addView(lab("HASIL GENERATE"))
        val resultView = Ui.text(act, "", 16.5f, Ui.TXT)
        fieldStyle(resultView)
        resultBox.addView(resultView)
        val cap = Ui.text(
            act,
            "8 digit awal sama dengan IMEI existing — perangkat dan model tetap sama. " +
                "Hanya nomor seri yang berubah, digit cek dihitung ulang.",
            12f, Ui.SUB
        )
        cap.setPadding(0, Ui.dp(act, 8), 0, 0)
        resultBox.addView(cap)
        val row2 = LinearLayout(act)
        row2.orientation = LinearLayout.HORIZONTAL
        val again = ghostButton("Generate lagi")
        again.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val copy = ghostButton("SALIN")
        copy.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row2.addView(again)
        row2.addView(copy)
        resultBox.addView(row2)
        val useBtn = Ui.button(act, "PAKAI SEBAGAI IMEI BARU")
        resultBox.addView(useBtn)
        val foot = Ui.text(
            act,
            "Hasil hanya kandidat yang formatnya valid; diterima jaringan atau tidak tergantung jaringan/registrasi.",
            12f, Ui.SUB
        )
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, Ui.dp(act, 12), 0, 0)
        resultBox.addView(foot)
        root.addView(resultBox)

        var generated = ""
        fun showResult(value: String) {
            generated = value
            val span = SpannableString(value)
            span.setSpan(
                ForegroundColorSpan(Ui.ACCENT), 0, 8,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            span.setSpan(
                StyleSpan(Typeface.BOLD), 0, 8,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            resultView.text = span
            resultBox.visibility = View.VISIBLE
        }
        fun doGenerate() {
            val existing = edit.text.toString()
            if (existing.length != 15 || !ImeiKit.isDigits(existing)) {
                Ui.toast(act, "Masukkan 15 digit IMEI existing dulu")
                return
            }
            showResult(ImeiKit.generateTACPreserving(existing))
        }
        genBtn.setOnClickListener { doGenerate() }
        again.setOnClickListener { doGenerate() }
        copy.setOnClickListener {
            if (generated.isEmpty()) return@setOnClickListener
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("IMEI", generated))
            Ui.toast(act, "Disalin")
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle("Generate IMEI")
            .setView(scrollWrap(root))
            .create()
        dlg.show()
        useBtn.setOnClickListener {
            if (generated.isEmpty()) return@setOnClickListener
            dlg.dismiss()
            onUse(generated)
        }
    }
}

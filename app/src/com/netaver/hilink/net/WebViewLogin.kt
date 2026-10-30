package com.netaver.hilink.net

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Login via hidden WebView (vc57): loads the router's real login page like a
 * browser, auto-fills the password and submits, then harvests the SessionID
 * cookie. The session obtained this way is 100% browser-identical.
 *
 * Must be called on the UI thread. Callback runs on the UI thread.
 */
object WebViewLogin {

    private const val POLL_MS = 500L
    private const val TIMEOUT_MS = 15_000L

    fun login(activity: Activity, host: String, password: String, cb: (Result<String>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { login(activity, host, password, cb) }
            return
        }
        try {
            val wv = WebView(activity)
            wv.layoutParams = FrameLayout.LayoutParams(1, 1)
            wv.visibility = android.view.View.INVISIBLE
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val ws = wv.settings
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.savePassword = false

            var done = false
            fun finish(r: Result<String>) {
                if (done) return
                done = true
                try {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                } catch (e: Exception) { }
                cb(r)
            }

            val startMs = System.currentTimeMillis()
            val poller = object : Runnable {
                override fun run() {
                    if (done) return
                    val sid = readSessionId(cm, host)
                    if (sid != null) {
                        finish(Result.success(sid)); return
                    }
                    if (System.currentTimeMillis() - startMs > TIMEOUT_MS) {
                        finish(Result.failure(Exception("Timeout menunggu cookie SessionID dari WebView")))
                        return
                    }
                    main.postDelayed(this, POLL_MS)
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Give the page's own JS a moment to render the login form.
                    main.postDelayed({
                        if (!done) view.evaluateJavascript(buildLoginJs(password), null)
                    }, 800)
                    // Also retry the injection a couple of times in case the form renders late.
                    main.postDelayed({
                        if (!done && readSessionId(cm, host) == null)
                            view.evaluateJavascript(buildLoginJs(password), null)
                    }, 2500)
                }

                override fun onReceivedError(
                    view: WebView, errorCode: Int, description: String, failingUrl: String
                ) {
                    finish(Result.failure(Exception("WebView gagal memuat $failingUrl: $description")))
                }
            }

            // Attach to decor view so the WebView actually renders.
            (activity.window.decorView as ViewGroup).addView(wv)
            main.postDelayed(poller, POLL_MS)
            wv.loadUrl("http://$host/")
        } catch (e: Exception) {
            cb(Result.failure(e))
        }
    }

    private fun readSessionId(cm: CookieManager, host: String): String? {
        return try {
            val raw = cm.getCookie("http://$host") ?: return null
            // raw looks like "SessionID=abc123; path=/"
            for (part in raw.split(";")) {
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].equals("SessionID", ignoreCase = true) && kv[1].isNotEmpty())
                    return kv[1]
            }
            null
        } catch (e: Exception) { null }
    }

    private fun jsEscape(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

    private fun buildLoginJs(password: String): String {
        val pw = jsEscape(password)
        return """(function(pw){
  try {
    var inputs = document.querySelectorAll('input[type="password"]');
    if (!inputs || inputs.length === 0) return 'NO_PW_FIELD';
    var f = inputs[0];
    f.focus();
    // set value via native setter so framework-bound inputs notice
    var proto = Object.getPrototypeOf(f);
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(f, pw); else f.value = pw;
    f.dispatchEvent(new Event('input', {bubbles:true}));
    f.dispatchEvent(new Event('change', {bubbles:true}));
    var btn = document.querySelector('#login_btn') ||
              document.querySelector('#btn_login') ||
              document.querySelector('.login_btn') ||
              document.querySelector('button[type="submit"]') ||
              document.querySelector('input[type="submit"]');
    if (btn) { btn.click(); return 'CLICKED'; }
    if (f.form) { f.form.submit(); return 'FORM_SUBMIT'; }
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      var t = (btns[i].innerText || '').toLowerCase();
      if (t.indexOf('login') >= 0 || t.indexOf('masuk') >= 0) { btns[i].click(); return 'CLICKED_TEXT'; }
    }
    var e = new KeyboardEvent('keydown', {key:'Enter', keyCode:13, bubbles:true});
    f.dispatchEvent(e);
    return 'ENTER_SENT';
  } catch (err) { return 'JS_ERR:' + err; }
})('$pw')"""
    }
}

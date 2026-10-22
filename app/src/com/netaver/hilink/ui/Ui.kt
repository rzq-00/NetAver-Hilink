package com.netaver.hilink.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** NetAver dark theme + tiny programmatic UI helpers + background runner. */
object Ui {
    val BG = Color.rgb(10, 14, 26)
    val CARD = Color.rgb(18, 26, 48)
    val CARD2 = Color.rgb(16, 24, 48)
    val BORDER = Color.rgb(30, 42, 74)
    val ACCENT = Color.rgb(91, 155, 255)
    val BTN = Color.rgb(47, 93, 168)
    val GREEN = Color.rgb(62, 207, 110)
    val RED = Color.rgb(242, 109, 109)
    val AMBER = Color.rgb(245, 192, 78)
    val TXT = Color.rgb(232, 236, 245)
    val SUB = Color.rgb(138, 148, 176)
    val FAINT = Color.rgb(104, 116, 143)

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun bg(color: Int, radius: Int, ctx: Context, border: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(ctx, radius).toFloat()
        if (border != 0) g.setStroke(dp(ctx, 1), border)
        return g
    }

    fun text(ctx: Context, s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun card(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.background = bg(CARD, 14, ctx, BORDER)
        l.setPadding(dp(ctx, 16), dp(ctx, 15), dp(ctx, 16), dp(ctx, 15))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 12)
        l.layoutParams = lp
        return l
    }

    fun button(ctx: Context, label: String, primary: Boolean = true): TextView {
        val t = text(ctx, label, 15.5f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(ctx, 14), 0, dp(ctx, 14))
        if (primary) {
            t.background = bg(BTN, 10, ctx)
        } else {
            t.background = bg(Color.TRANSPARENT, 10, ctx, Color.rgb(163, 51, 51))
            t.setTextColor(RED)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 12)
        t.layoutParams = lp
        return t
    }

    fun chip(ctx: Context, label: String, selected: Boolean): TextView {
        val t = text(ctx, label, 12.5f, if (selected) Color.WHITE else Color.rgb(174, 185, 212), true)
        t.setPadding(dp(ctx, 11), dp(ctx, 6), dp(ctx, 11), dp(ctx, 6))
        t.background = bg(if (selected) BTN else Color.rgb(26, 36, 71), 20, ctx)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(ctx, 6); lp.bottomMargin = dp(ctx, 8)
        t.layoutParams = lp
        return t
    }

    /**
     * In-app banner instead of a system Toast: Android truncates long
     * Toast texts (~2 lines, worse on vendor skins), which cut off the
     * router error messages. The banner shows the FULL text, wraps as
     * needed, stays a bit longer for longer texts, and can be tapped
     * away. Falls back to a Toast only when no Activity is at hand.
     */
    fun toast(ctx: Context, msg: String) {
        val act = findActivity(ctx)
        if (act == null) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            return
        }
        act.runOnUiThread { showBanner(act, msg) }
    }

    private fun findActivity(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c != null) {
            if (c is Activity) return c
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    private var banner: TextView? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable {
        banner?.let { (it.parent as? ViewGroup)?.removeView(it) }
        banner = null
    }

    private fun showBanner(act: Activity, msg: String) {
        val root = act.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return
        bannerHandler.removeCallbacks(hideBanner)
        banner?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val tv = TextView(act)
        tv.text = msg
        tv.textSize = 14.5f
        tv.setTextColor(TXT)
        tv.background = bg(Color.rgb(28, 40, 72), 12, act, BORDER)
        tv.setPadding(dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13))
        tv.elevation = dp(act, 8).toFloat()
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM
        lp.leftMargin = dp(act, 16)
        lp.rightMargin = dp(act, 16)
        lp.bottomMargin = dp(act, 88)
        tv.layoutParams = lp
        tv.setOnClickListener {
            bannerHandler.removeCallbacks(hideBanner)
            hideBanner.run()
        }
        root.addView(tv)
        banner = tv
        tv.alpha = 0f
        tv.animate().alpha(1f).setDuration(150).start()
        val dwell = (3500L + msg.length * 45L).coerceAtMost(9500L)
        bannerHandler.postDelayed(hideBanner, dwell)
    }

    /**
     * Password field with an eye toggle inside its right edge (mockup
     * approved 2026-10-22): container styled like [fld], the EditText is
     * borderless inside it; tapping the eye flips the transformation
     * method so the user can check for typos. Returns container + field.
     */
    fun passwordField(act: Activity, hint: String): Pair<LinearLayout, EditText> {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = bg(Color.rgb(13, 20, 36), 10, act, BORDER)
        val e = EditText(act)
        e.hint = hint
        e.setTextColor(TXT); e.setHintTextColor(FAINT); e.textSize = 15.5f
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.transformationMethod = PasswordTransformationMethod.getInstance()
        e.background = null
        e.setPadding(dp(act, 14), dp(act, 12), 0, dp(act, 12))
        e.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        box.addView(e)
        var hidden = true
        val eye = ImageView(act)
        eye.setImageDrawable(EyeDrawable(act, false))
        eye.scaleType = ImageView.ScaleType.FIT_CENTER
        eye.setPadding(dp(act, 11), 0, dp(act, 11), 0)
        eye.layoutParams = LinearLayout.LayoutParams(dp(act, 46), ViewGroup.LayoutParams.MATCH_PARENT)
        eye.setOnClickListener {
            hidden = !hidden
            val pos = e.selectionStart
            e.transformationMethod =
                if (hidden) PasswordTransformationMethod.getInstance() else null
            eye.setImageDrawable(EyeDrawable(act, !hidden))
            if (pos >= 0) e.setSelection(pos)
        }
        box.addView(eye)
        val blp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.bottomMargin = dp(act, 4)
        box.layoutParams = blp
        return Pair(box, e)
    }

    /** Eye icon drawn in code (no bundled assets): outline eye + pupil;
     * the slashed variant is the "visible" state. */
    class EyeDrawable(ctx: Context, private val slashed: Boolean) : Drawable() {
        private val unit = dp(ctx, 1).toFloat()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SUB; style = Paint.Style.STROKE; strokeWidth = 1.8f * unit
            strokeCap = Paint.Cap.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SUB; style = Paint.Style.FILL
        }
        override fun getIntrinsicWidth(): Int = (24 * unit).toInt()
        override fun getIntrinsicHeight(): Int = (24 * unit).toInt()
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val w = b.width() * 0.34f; val h = b.height() * 0.20f
            val path = Path()
            path.moveTo(cx - w, cy)
            path.quadTo(cx, cy - 2 * h, cx + w, cy)
            path.quadTo(cx, cy + 2 * h, cx - w, cy)
            canvas.drawPath(path, paint)
            canvas.drawCircle(cx, cy, b.height() * 0.10f, fill)
            if (slashed) canvas.drawLine(cx - w * 0.85f, cy + h * 1.6f, cx + w * 0.85f, cy - h * 1.6f, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; fill.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; fill.colorFilter = cf }
        @Deprecated("Drawable opacity")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    fun confirm(act: Activity, title: String, msg: String, okLabel: String, onOk: () -> Unit) {
        AlertDialog.Builder(act)
            .setTitle(title).setMessage(msg)
            .setNegativeButton("Batal", null)
            .setPositiveButton(okLabel) { _, _ -> onOk() }
            .show()
    }
}

/** Single background executor: router calls stay ordered; results land on the UI thread. */
object Bg {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "hilink-bg").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        exec.execute {
            val res = try { Result.success(work()) } catch (e: Exception) { Result.failure(e) }
            main.post { done(res) }
        }
    }
}

interface Screen {
    fun build(act: Activity): View
    fun onShow() {}
    fun onHide() {}
}

package com.netaver.hilink.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.netaver.hilink.R

/** Host activity: bottom tabs (Dashboard, Band, Mode, SMS, Login, Setelan) + sub-screens. */
class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private val tabViews = mutableListOf<TextView>()
    private var screens = mutableMapOf<Int, Screen>()
    private var built = mutableMapOf<Int, View>()
    private var current = -1

    companion object {
        const val TAB_DASHBOARD = 0
        const val TAB_BAND = 1 // sub-screen since v2.2.2 (opened from Dashboard chips), highlights Dashboard
        const val TAB_MODE = 2 // sub-screen since v2.2.2, highlights Dashboard
        const val TAB_SMS = 3 // sub-screen since v2.2.2, highlights Dashboard
        const val TAB_SETTINGS = 4
        const val TAB_DEVICES = 5 // sub-screen, highlights Dashboard
        const val TAB_WIFI = 6 // router-settings sub-screens, highlight Setelan
        const val TAB_DHCP = 7
        const val TAB_USSD = 8
        const val TAB_APN = 9
        const val TAB_POINTING = 11 // sub-screen, highlights Dashboard
        const val TAB_LOGIN = 12 // main tab (own bar slot since v2.2.0)
        const val TAB_WIFIANALISA = 13 // router-settings sub-screen (from WiFi tab), highlights Setelan
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(content)

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setBackgroundColor(Color.rgb(13, 20, 36))
        tabs.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6))
        val barOrder = listOf(TAB_DASHBOARD, TAB_LOGIN, TAB_SETTINGS)
        val labels = listOf("DASHBOARD", "LOGIN", "SETELAN")
        labels.forEachIndexed { i, label ->
            val t = Ui.text(this, label, 11f, Ui.FAINT, true)
            t.gravity = Gravity.CENTER
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
            val tab = barOrder[i]
            t.setOnClickListener { show(tab) }
            tabs.addView(t)
            tabViews.add(t)
        }
        root.addView(tabs)
        setContentView(root)
        show(TAB_DASHBOARD)
    }

    fun show(tab: Int) {
        if (tab == current) return
        if (current >= 0) screens[current]?.onHide()
        val screen = screens.getOrPut(tab) {
            when (tab) {
                TAB_DASHBOARD -> DashboardScreen(this)
                TAB_BAND -> BandScreen(this)
                TAB_MODE -> ModeScreen(this)
                TAB_SMS -> SmsScreen(this)
                TAB_SETTINGS -> SettingsScreen(this)
                TAB_WIFI -> WifiScreen(this)
                TAB_DHCP -> DhcpScreen(this)
                TAB_USSD -> UssdScreen(this)
                TAB_APN -> ApnScreen(this)
                TAB_POINTING -> PointingScreen(this)
                TAB_LOGIN -> LoginScreen(this)
                TAB_WIFIANALISA -> WifiAnalisaScreen(this)
                else -> DevicesScreen(this)
            }
        }
        val view = built.getOrPut(tab) { screen.build(this) }
        content.removeAllViews()
        content.addView(view)
        current = tab
        val highlightPos = when (tab) {
            TAB_LOGIN -> 1
            TAB_SETTINGS, TAB_WIFI, TAB_DHCP, TAB_USSD, TAB_APN, TAB_WIFIANALISA -> 2 // Setelan slot
            else -> 0 // Dashboard slot: Dashboard itself + Band/Mode/SMS/Devices/Pointing sub-screens
        }
        tabViews.forEachIndexed { i, t -> t.setTextColor(if (i == highlightPos) Ui.ACCENT else Ui.FAINT) }
        screen.onShow()
    }

    override fun onBackPressed() {
        when {
            current == TAB_WIFIANALISA -> show(TAB_WIFI)
            current in listOf(TAB_WIFI, TAB_DHCP, TAB_USSD, TAB_APN) -> show(TAB_SETTINGS)
            current != TAB_DASHBOARD -> show(TAB_DASHBOARD)
            else -> super.onBackPressed()
        }
    }

    fun app(): com.netaver.hilink.HiLinkApp = application as com.netaver.hilink.HiLinkApp
    fun logoRes(): Int = R.drawable.logo_netaver

    /** Callback untuk hasil runtime permission (tanpa AndroidX). */
    interface PermResult {
        fun onPermResult(requestCode: Int, granted: Boolean)
    }

    private var permHandler: PermResult? = null

    fun requestPerm(perm: String, code: Int, handler: PermResult) {
        permHandler = handler
        requestPermissions(arrayOf(perm), code)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val h = permHandler; permHandler = null
        h?.onPermResult(requestCode,
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
    }

    fun wifiScreen(): WifiScreen? = screens[TAB_WIFI] as? WifiScreen
}

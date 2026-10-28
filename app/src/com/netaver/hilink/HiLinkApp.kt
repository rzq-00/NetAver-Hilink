package com.netaver.hilink

import android.app.Application
import com.netaver.hilink.core.LanGuard
import com.netaver.hilink.net.HilinkClient
import com.netaver.hilink.store.Prefs

/** Application singleton: prefs + the ONE shared HilinkClient (single session). */
class HiLinkApp : Application() {
    lateinit var prefs: Prefs; private set
    private var client: HilinkClient? = null
    private var clientKey = ""

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    @Synchronized
    fun client(): HilinkClient {
        // Choke point (v2.3.1 vc26): whatever path stored the router address —
        // login form, a DHCP gateway change, an old install — the client is only
        // ever built for a LanGuard-approved LAN address, and the normalized
        // host is what gets used. A stored public address fails loudly here
        // instead of ever receiving the router password.
        val guard = LanGuard.check(prefs.routerIp)
        if (guard is LanGuard.Result.Bad) throw IllegalStateException(guard.reason)
        val host = (guard as LanGuard.Result.Ok).host
        val key = host + "|" + prefs.username + "|" + prefs.password().hashCode()
        if (client == null || key != clientKey) {
            client = HilinkClient(host, prefs.username, prefs.password(), prefs.loginScheme)
            client!!.onSchemeLearned = { s -> prefs.loginScheme = s }
            clientKey = key
        }
        return client!!
    }

    /** Drop the client (after logout or credential change). */
    @Synchronized
    fun resetClient() { client = null; clientKey = "" }
}

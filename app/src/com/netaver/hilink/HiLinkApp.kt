package com.netaver.hilink

import android.app.Application
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
        val key = prefs.routerIp + "|" + prefs.username + "|" + prefs.password().hashCode()
        if (client == null || key != clientKey) {
            client = HilinkClient(prefs.routerIp, prefs.username, prefs.password(), prefs.loginScheme)
            client!!.onSchemeLearned = { s -> prefs.loginScheme = s }
            clientKey = key
        }
        return client!!
    }

    /** Drop the client (after logout or credential change). */
    @Synchronized
    fun resetClient() { client = null; clientKey = "" }
}

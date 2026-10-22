package com.netaver.hilink.store

import android.content.Context
import android.content.SharedPreferences

/** App settings. Password lives in SecureStore (Keystore ciphertext only). */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("netaver_hilink", Context.MODE_PRIVATE)

    var routerIp: String
        get() = sp.getString("router_ip", "192.168.8.1") ?: "192.168.8.1"
        set(v) { sp.edit().putString("router_ip", v).apply() }

    var username: String
        get() = sp.getString("username", "admin") ?: "admin"
        set(v) { sp.edit().putString("username", v).apply() }

    var refreshSecs: Int
        get() = sp.getInt("refresh_secs", 10)
        set(v) { sp.edit().putInt("refresh_secs", v).apply() }

    /** Login hash scheme that worked on this router (-1 = unknown yet). */
    var loginScheme: Int
        get() = sp.getInt("login_scheme", -1)
        set(v) { sp.edit().putInt("login_scheme", v).apply() }

    @Volatile private var memPassword: String? = null

    fun password(): String {
        memPassword?.let { return it }
        // migrate a v1.0 plaintext password (if any) into the Keystore store
        val legacy = sp.getString("password", null)
        if (legacy != null) {
            setPassword(legacy)
            sp.edit().remove("password").apply()
            memPassword?.let { return it }
            return legacy
        }
        val dec = SecureStore.decrypt(sp.getString("password_enc", null))
        if (dec.isNotEmpty()) memPassword = dec
        return dec
    }

    /** true when the password was persisted encrypted; false = memory-only. */
    fun setPassword(v: String): Boolean {
        memPassword = v
        val enc = SecureStore.encrypt(v)
        return if (enc != null) {
            sp.edit().putString("password_enc", enc).apply()
            true
        } else {
            sp.edit().remove("password_enc").apply()
            false
        }
    }
}

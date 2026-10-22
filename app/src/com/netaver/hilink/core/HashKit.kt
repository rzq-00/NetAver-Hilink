package com.netaver.hilink.core

import java.security.MessageDigest

/**
 * Huawei login hash schemes — mirrored EXACTLY from the stock WebUI login.js:
 * their SHA256() returns a HEX string and base64encode() encodes THAT TEXT.
 * (Encoding the raw digest bytes instead produces a completely different
 * value and the router answers "wrong password" — the v2.0/v1.0 bug.)
 *
 * scheme 4: b64( hex_sha256( user + b64( hex_sha256(password) ) + token ) )
 * scheme 1: b64( hex_sha256(password) )
 */
object HashKit {
    fun sha256Hex(text: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(String.format("%02x", b.toInt() and 0xFF))
        return sb.toString()
    }

    fun b64Text(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    /** The inner value, also the complete scheme-1 password. */
    fun inner(password: String): String = b64Text(sha256Hex(password))

    fun scheme4(username: String, password: String, token: String): String =
        b64Text(sha256Hex(username + inner(password) + token))
}

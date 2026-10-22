package com.netaver.hilink.core

/** Some firmwares return USSD replies as UCS-2 hex; decode those, pass plain text through. */
object UssdText {
    fun decode(raw: String): String {
        val t = raw.trim()
        if (t.length >= 8 && t.length % 4 == 0 && t.all { isHex(it) }) {
            try {
                val bytes = ByteArray(t.length / 2)
                var i = 0
                while (i < bytes.size) {
                    bytes[i] = ((hexVal(t[i * 2]) shl 4) or hexVal(t[i * 2 + 1])).toByte()
                    i++
                }
                val s = String(bytes, Charsets.UTF_16BE)
                // Accept only if it decodes to plausible Latin text (menus like "1234" must pass through untouched)
                if (s.length >= 2 && s.all { it.code < 0x250 || it.isWhitespace() }) return s
            } catch (e: Exception) { /* fall through to plain */ }
        }
        return t
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    private fun hexVal(c: Char) = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        else -> c - 'A' + 10
    }
}

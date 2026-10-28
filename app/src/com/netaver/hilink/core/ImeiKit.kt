package com.netaver.hilink.core

/**
 * IMEI helpers (pure logic, no Android APIs).
 *
 * IMEI layout (15 digits): TAC (8 digits, maker + model) + serial (6 digits)
 * + check digit (1 digit, Luhn). A TAC-preserving generate keeps the first 8
 * digits exactly, randomizes only the serial, and recomputes the check digit —
 * so the device/model identification (TAC) stays identical.
 */
object ImeiKit {
    fun isDigits(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' }

    /** Luhn check digit for a 14-digit base (TAC + serial). Doubled positions
     *  are the even 1-based positions from the left (2, 4, …, 14). */
    fun luhnCheckDigit(base14: String): Int {
        var sum = 0
        for (i in base14.indices) {
            var d = base14[i] - '0'
            if ((i + 1) % 2 == 0) {
                d *= 2
                sum += d / 10 + d % 10
            } else {
                sum += d
            }
        }
        return (10 - (sum % 10)) % 10
    }

    fun isValidImei(s: String): Boolean {
        if (s.length != 15 || !isDigits(s)) return false
        return luhnCheckDigit(s.substring(0, 14)) == (s[14] - '0')
    }

    /** Masked display, as in the mockup: first 4 + 8 bullets + last 3. */
    fun maskImei(imei: String): String {
        if (imei.length < 7) return imei
        return imei.substring(0, 4) + "••••••••" + imei.substring(imei.length - 3)
    }

    /** TAC-preserving generate: same TAC, new serial (never the original
     *  serial, never all zeros), fresh Luhn check digit. */
    fun generateTACPreserving(existing: String): String {
        require(existing.length == 15 && isDigits(existing)) { "IMEI harus 15 digit angka" }
        val tac = existing.substring(0, 8)
        val origSerial = existing.substring(8, 14)
        val rnd = java.security.SecureRandom()
        var serial: String
        var tries = 0
        do {
            serial = String.format(java.util.Locale.US, "%06d", rnd.nextInt(1_000_000))
            tries++
        } while ((serial == origSerial || serial == "000000") && tries < 100)
        if (serial == origSerial || serial == "000000") {
            serial = if (origSerial != "000001") "000001" else "000002"
        }
        val base14 = tac + serial
        return base14 + luhnCheckDigit(base14).toString()
    }
}

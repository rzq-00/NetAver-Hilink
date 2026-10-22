package com.netaver.hilink.core

/** LTE band model: hex bitmask, bit (n-1) set = band n allowed. Pure logic. */
object Bands {
    data class Band(val n: Int, val freq: String, val duplex: String)

    val ALL: List<Band> = listOf(
        Band(1, "2100 MHz", "FDD"), Band(2, "1900 MHz", "FDD"), Band(3, "1800 MHz", "FDD"),
        Band(5, "850 MHz", "FDD"), Band(7, "2600 MHz", "FDD"), Band(8, "900 MHz", "FDD"),
        Band(20, "800 MHz", "FDD"), Band(28, "700 MHz", "FDD"),
        Band(38, "2600 MHz", "TDD"), Band(40, "2300 MHz", "TDD"), Band(41, "2500 MHz", "TDD")
    )
    const val ALL_HEX = "7FFFFFFFFFFFFFFF"
    const val NETWORK_BAND_ALL = "3FFFFFFF"

    fun maskOf(bands: Set<Int>): String {
        var v = java.math.BigInteger.ZERO
        for (b in bands) v = v.setBit(b - 1)
        return v.toString(16).uppercase()
    }

    fun parseMask(hex: String?): Set<Int> {
        if (hex.isNullOrBlank()) return emptySet()
        var h = hex.trim()
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2)
        val v = try { java.math.BigInteger(h, 16) } catch (e: Exception) { return emptySet() }
        val out = mutableSetOf<Int>()
        for (b in ALL) if (v.testBit(b.n - 1)) out.add(b.n)
        return out
    }

    fun isAll(hex: String?): Boolean {
        if (hex.isNullOrBlank()) return false
        return hex.trim().equals(ALL_HEX, ignoreCase = true)
    }
}

/** Network mode codes used by /api/net/net-mode. */
object NetModes {
    const val AUTO = "00"
    const val GSM_ONLY = "01"
    const val WCDMA_ONLY = "02"
    const val LTE_ONLY = "03"
    val OPTIONS = listOf(
        AUTO to "Otomatis (4G / 3G / 2G)",
        LTE_ONLY to "4G saja",
        WCDMA_ONLY to "3G saja",
        GSM_ONLY to "2G saja"
    )
    fun label(code: String): String = OPTIONS.firstOrNull { it.first == code }?.second ?: "Mode $code"
}

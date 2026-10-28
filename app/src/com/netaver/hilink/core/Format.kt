package com.netaver.hilink.core

/** Display formatting + signal grading. Pure logic. */
object Format {
    /** Some firmwares embed units in signal values ("-73dBm", "-8dB"); strip them so the UI adds units exactly once. */
    fun stripUnit(s: String): String =
        s.trim().replace(Regex("(?i)\\s*(dbm|db)$"), "").trim()

    /** LTE band from a downlink EARFCN (3GPP TS 36.101 ranges), null when outside all known ranges. */
    fun bandFromEarfcn(earfcn: Int): Int? {
        val ranges = listOf(
            0..599 to 1, 600..1199 to 2, 1200..1949 to 3, 1950..2399 to 4, 2400..2649 to 5,
            2650..2749 to 6, 2750..3449 to 7, 3450..3799 to 8, 3800..4149 to 9, 4150..4749 to 10,
            4750..4949 to 11, 4950..5279 to 12, 5280..5379 to 13, 5380..5479 to 14,
            5730..5849 to 17, 5850..5999 to 18, 6000..6149 to 19, 6150..6449 to 20,
            6450..6599 to 21, 6600..7399 to 22, 7400..7699 to 23, 7700..8039 to 24,
            8040..8689 to 25, 8690..9039 to 26, 9040..9209 to 27, 9210..9659 to 28,
            9660..9779 to 29, 9780..9869 to 30, 9870..9919 to 31, 9920..10359 to 32,
            36000..36199 to 33, 36200..36349 to 34, 36350..36949 to 35, 36950..37549 to 36,
            37550..37749 to 37, 37750..38249 to 38, 38250..38649 to 39, 38650..39649 to 40,
            39650..41589 to 41, 41590..43589 to 42, 43590..45589 to 43, 45590..47589 to 44,
            65536..66435 to 65, 66436..67335 to 66, 68586..68935 to 71,
        )
        return ranges.firstOrNull { earfcn in it.first }?.second
    }

    /** First integer found in a string ("LTE Band 3" -> 3), null when none. */
    fun firstInt(s: String): Int? = Regex("\\d+").find(s)?.value?.toIntOrNull()

    /**
     * Serving band dug out of a raw XML payload at ANY nesting depth.
     * Only an element named exactly "band" counts (never "bandwidth" = MHz, never the LTEBand lock mask);
     * otherwise DL EARFCN elements are converted via the 3GPP table.
     */
    fun bandFromRawXml(raw: String): Int? {
        if (raw.isEmpty()) return null
        val bandTag = Regex("(?i)<band(\\s[^>]*)?>\\s*([^<]*)").find(raw)
        if (bandTag != null) {
            val n = firstInt(bandTag.groupValues[2])
            if (n != null && n in 1..88) return n
        }
        val ear = Regex("(?i)<(earfcn\\d*|dl_earfcn|earfcndl|earfcn_dl)(\\s[^>]*)?>[^<]*?(\\d+)").find(raw)
        if (ear != null) {
            ear.groupValues[3].toIntOrNull()?.let { return bandFromEarfcn(it) }
        }
        return null
    }

    fun bytes(n: Long): String {
        if (n < 0) return "0 B"
        val u = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = n.toDouble(); var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return if (i == 0) "$n B" else String.format("%.1f %s", v, u[i]).replace('.', ',')
    }

    fun duration(totalSec: Long): String {
        if (totalSec <= 0) return "—"
        val d = totalSec / 86400; val h = (totalSec % 86400) / 3600
        val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (d > 0) "$d hari " + String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d:%02d", h, m, s)
    }

    /** RSRP dBm -> (label, 0..5 bars) */
    fun gradeRsrp(dbm: Int): Pair<String, Int> = when {
        dbm >= -80 -> "Sangat bagus" to 5
        dbm >= -90 -> "Bagus" to 4
        dbm >= -100 -> "Cukup" to 3
        dbm >= -110 -> "Lemah" to 2
        else -> "Sangat lemah" to 1
    }

    /**
     * RAT label for /api/monitoring/status CurrentNetworkType.
     * Ground truth on the E5577: code 19 = LTE (101 on some other firmwares).
     * LTE-only metrics (RSRP/RSRQ) in the signal report always win over the code.
     */
    fun ratLabel(code: String, hasLteMetrics: Boolean, has3gMetrics: Boolean): String {
        if (hasLteMetrics) return "4G LTE"
        return when (code) {
            "0" -> "Tidak ada layanan"
            "1" -> "GSM (2G)"
            "2" -> "GPRS (2G)"
            "3" -> "EDGE (2G)"
            "4" -> "WCDMA (3G)"
            "5" -> "HSDPA (3G)"
            "6" -> "HSUPA (3G)"
            "7" -> "HSPA (3G)"
            "8" -> "TD-SCDMA (3G)"
            "9", "17", "18" -> "HSPA+ (3G)"
            "19", "101" -> "4G LTE"
            else -> if (has3gMetrics) "3G" else if (code.isBlank()) "\u2014" else "Tipe $code"
        }
    }

    fun maskImei(imei: String): String =
        if (imei.length <= 4) imei else "••••••••••" + imei.takeLast(4)

    /** Decimal string -> uppercase hex ("" when not a number). For LAC / Cell ID displays. */
    fun hexUpper(dec: String): String {
        val v = dec.trim().toLongOrNull() ?: return ""
        return v.toString(16).uppercase()
    }

    /** LTE Cell ID -> (eNB id, sector) as decimal strings. */
    fun enbSector(cellId: String): Pair<String, String> {
        val v = cellId.trim().toLongOrNull() ?: return "" to ""
        return (v shr 8).toString() to (v and 0xFF).toString()
    }

    // Pointing-screen quality colors (match the mod WebUI's antennapointing palette).
    fun rsrpColor(v: Double): Int = when {
        v >= -85.0 -> 0xFF4CD964.toInt()
        v >= -100.0 -> 0xFFFFA726.toInt()
        else -> 0xFFFF5252.toInt()
    }

    fun sinrColor(v: Double): Int = when {
        v >= 10.0 -> 0xFF4CD964.toInt()
        v >= 0.0 -> 0xFFFFA726.toInt()
        else -> 0xFFFF5252.toInt()
    }

    fun rsrqColor(v: Double): Int = when {
        v >= -15.0 -> 0xFF4CD964.toInt()
        v >= -19.0 -> 0xFFFFA726.toInt()
        else -> 0xFFFF5252.toInt()
    }

    fun rssiColor(v: Double): Int = when {
        v >= -65.0 -> 0xFF4CD964.toInt()
        v >= -85.0 -> 0xFFFFA726.toInt()
        else -> 0xFFFF5252.toInt()
    }
}

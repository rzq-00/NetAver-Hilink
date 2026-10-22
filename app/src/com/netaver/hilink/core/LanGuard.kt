package com.netaver.hilink.core

/**
 * Safety guard: the app may only talk to LAN addresses, so the router
 * password can never be sent to a public server by mistake. Pure logic.
 */
object LanGuard {
    sealed class Result {
        data class Ok(val host: String) : Result()
        data class Bad(val reason: String) : Result()
    }

    fun check(raw: String): Result {
        var h = raw.trim()
        if (h.startsWith("http://")) h = h.substring(7)
        if (h.startsWith("https://")) h = h.substring(8)
        h = h.substringBefore('/').substringBefore(':').trim()
        if (h.isEmpty()) return Result.Bad("Alamat router kosong")
        if (h.equals("localhost", ignoreCase = true)) return Result.Ok("127.0.0.1")
        val parts = h.split(".")
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all { c -> c.isDigit() } }) {
            val o = parts.map { it.toIntOrNull() ?: -1 }
            if (o.any { it < 0 || it > 255 }) return Result.Bad("Alamat IP tidak valid: $h")
            val private = o[0] == 10 || (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168) || (o[0] == 169 && o[1] == 254)
            return if (private) Result.Ok(h)
            else Result.Bad("Demi keamanan, app hanya menghubungi alamat LAN (10.x, 172.16–31.x, 192.168.x). \"$h\" adalah alamat publik — ditolak agar password router tidak terkirim ke internet.")
        }
        // Hostnames: single label (router, huawei) or *.local / *.home / *.lan only
        val lower = h.lowercase()
        val okName = !lower.contains('.') ||
            lower.endsWith(".local") || lower.endsWith(".home") || lower.endsWith(".lan")
        return if (okName && lower.all { it.isLetterOrDigit() || it == '.' || it == '-' }) Result.Ok(lower)
        else Result.Bad("Nama host \"$h\" tidak dikenali sebagai alamat LAN — gunakan alamat IP lokal router (mis. 192.168.8.1).")
    }
}

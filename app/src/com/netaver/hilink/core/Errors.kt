package com.netaver.hilink.core

/** HiLink error codes -> friendly Indonesian messages. Pure logic. */
object Errors {
    fun describe(code: String): String = when (code) {
        "108001" -> "Username atau password salah"
        "108002" -> "Belum login — sesi diperlukan"
        "108003" -> "Sudah ada sesi login lain yang aktif di router. Tunggu sebentar, atau logout dari perangkat/WebUI lain dulu."
        "108006" -> "Password router salah"
        "108007" -> "Terlalu banyak percobaan login gagal — router memblokir sementara (sekitar 2 menit). App berhenti mencoba selama masa blokir."
        "100002" -> "Parameter tidak didukung router"
        "100003" -> "Tidak ada izin untuk operasi ini"
        "100004" -> "Router sibuk — coba lagi sebentar"
        "113001" -> "Token sesi tidak cocok — app akan login ulang"
        "113002" -> "Format data ditolak router"
        "125001" -> "Sesi tidak dikenal — app akan login ulang"
        "125002" -> "Belum login — sesi diperlukan"
        "125003" -> "Sesi kedaluwarsa atau tidak valid — app akan login ulang"
        "125004" -> "Token sesi tidak cocok — app akan login ulang"
        "111019" -> "Operator belum merespons permintaan USSD (111019) — menurut kode WebUI router sendiri, ini berarti belum ada jawaban dari jaringan, bukan penolakan. Aplikasi sudah menunggu dan mengulang otomatis sampai ±2 menit sebelum menyerah."
        "111013" -> "USSD gagal — modem sedang tidak terhubung ke jaringan (tidak ada sinyal / koneksi putus). Coba lagi saat modem terhubung, dan pastikan kodenya sesuai operator SIM."
        else -> "Router menjawab error $code"
    }

    /** Codes where ONE silent re-login + retry is the right reaction. */
    fun isSessionError(code: String): Boolean =
        code in setOf("108002", "125001", "125002", "125003", "113001", "125004")
}

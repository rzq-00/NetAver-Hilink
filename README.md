# NetAver HiLink

Aplikasi pengelola router/modem Huawei (HiLink API) — native Kotlin, tanpa WebView.

## Riwayat versi

### v2.0 (2026-10-21) — tulis ulang Kotlin + mesin sesi baru
- Tulis ulang penuh dari Java (v1.0) ke Kotlin native; UI programmatic tema gelap NetAver.
- **Sesi login TUNGGAL** untuk seluruh app: login sekali, dipakai semua layar; re-login senyap hanya saat sesi benar-benar kedaluwarsa (menjawab penyakit login-berulang yang memicu blokir 108007).
- Skema hash password mengikuti `state-login` router (password_type 4/1) dan diingat per router; token dicoba dari SesTokInfo lalu `webserver/token` (toleran WebUI mod).
- Blokir login 108007 dihormati app: tidak ada percobaan login selama masa blokir (±2 menit) + hitung mundur ditampilkan.
- **Keamanan:** password router terenkripsi Android Keystore (AES-GCM) — plaintext tidak pernah disimpan (migrasi otomatis dari v1.0); **penjaga LAN-only**: app menolak alamat publik agar kredensial tidak terkirim ke internet; logout menutup sesi di router; allowBackup=false; respon router dibatasi 2 MB.
- Fungsi lengkap: Dashboard (status/operator/RSRP/RSRQ/SINR/RSSI/PCI/Cell ID, traffic sesi+bulanan+rate, baterai, uptime, SMS belum dibaca, jumlah perangkat), Kunci Band LTE (11 band FDD/TDD + semua-band), Mode Jaringan (Auto/4G/3G/2G + saklar data + pindai operator PLMN), SMS native (baca/balas/kirim/hapus), Perangkat (daftar host, info device, antena bila didukung modem, hapus statistik, speed test, matikan router, reset pabrik konfirmasi ganda), Setelan (tes koneksi tanpa membakar percobaan login, status sesi, interval refresh 5/10/30/manual, Buka WebUI).
- Error router diterjemahkan ke Bahasa Indonesia.

### v1.0 (2026-10-19)
- Rilis pertama (Java): dashboard, band lock 10 band, SMS, setelan + reboot. Kode tersimpan di `legacy-v1/`.

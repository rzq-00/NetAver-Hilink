## v2.3.1 — 2026-10-22

Rilis ini isinya perbaikan dari hasil pemakaian langsung di router beneran — beberapa bug memang cuma ketahuan pas dipakai harian:

- **Notifikasi nggak kepotong lagi.** Dulu pesan panjang, apalagi pesan error dari router, tampilnya cuma sepotong gara-gara Toast bawaan Android. Sekarang notifikasi tampil sebagai banner di dalam aplikasi: teksnya utuh, bisa multi-baris, lama tampilnya mengikuti panjang teks. Ketuk saja kalau mau menutup cepat.
- **Channel & bandwidth WiFi sekarang terbaca dengan benar.** Kemarin aplikasi membaca dari tempat yang salah, jadi channel selalu tampil "Otomatis" padahal router sedang jalan di channel 11 / 40 MHz. Sekarang sumber bacanya sama persis seperti WebUI router, dan menyimpan pengaturannya juga mengikuti cara WebUI — jadi yang diubah benar-benar terpakai.
- **Ada tombol mata di kolom password.** Buat mengecek yang diketik sudah benar atau belum. Ada di halaman login, password WiFi, dan password APN.
- **LAN & DHCP dibenerin, sekalian dapat kolom DNS.** Subnet mask kemarin selalu kosong dan saklar DHCP ternyata tidak berpengaruh apa-apa — dua-duanya karena aplikasi memakai nama kunci yang beda dari yang dipakai firmware. Sudah dibetulkan mengikuti firmware-nya langsung. Plus kolom DNS Primer & Sekunder baru: isi kalau mau DNS manual, kosongkan kalau mau ikut operator.
- **Layar kunci band lebih bersih.** Teks "info dukungan modem" yang isinya XML mentah sudah dibuang — tidak penting dan cuma bikin penuh layar.

## v2.3.0 — 2026-10-22

WiFi lanjutan dari halaman WLAN Advanced WebUI firmware (wlanadvanced.js dari
paket mod, .js.gz): layar WiFi kini membaca `api/wlan/multi-basic-settings`
(profil jamak — menjelaskan SSID "E5577S" vs "3SinyalKuatHemat": dua profil
2.4 GHz di router yang sama).

- Per band: Channel (Otomatis/daftar; 2.4G 1–13, 5G daftar umum), Bandwidth
  (Otomatis/20/40 MHz), Mode (802.11…). Bagian band hanya tampil bila router
  melaporkan profil band itu (adaptif — unit E5577 Maulana 2.4G saja, MiFi
  dual-band akan menampilkan keduanya).
- Toggle Isolasi AP (WifiIsolate, berlaku semua profil).
- Tulis = read-modify-write blok profil mentah: hanya WifiChannel/WifiMode/
  wifibandwidth/WifiIsolate (+WifiRestart=1) yang ditambal; semua field lain
  diteruskan byte-identik seperti WebUI sendiri. Hasil sebagian dilaporkan
  jujur (dasar ✓/lanjutan gagal dsb).
- Diagnostik: check baru "Profil WLAN (multi-basic)" (ssid/mode/ch/bw/isolasi/
  negara per profil).
## v2.2.3 (2026-10-21)
- **USSD ditulis ulang mengikuti protokol WebUI router sendiri** (dari ussd.js bawaan paket WebUI mod, yang akhirnya terbaca setelah membongkar berkas .js.gz-nya): (1) dialog USSD yang menggantung kini **ditutup dulu** lewat `api/ussd/release` sebelum mengirim — sebelumnya aplikasi tidak pernah memanggil endpoint ini sama sekali; (2) kirim **sekali saja** dengan bentuk baku WebUI `{content, codeType: "CodeType", timeout: ""}` — skema multi-varian 6 bentuk dihapus; (3) polling jawaban **sabar sampai ±2 menit** dan menoleransi error 111019, yang menurut kode WebUI berarti "belum ada respons dari jaringan" (bukan penolakan) — sebelumnya aplikasi menyerah pada error pertama setelah ±14 detik; (4) dialog ditutup ulang di akhir kecuali sesi menu masih terbuka. Teks error 111019 diperbaiki sesuai arti sebenarnya (sebelumnya keliru menyarankan restart router).

## v2.2.2 (2026-10-21)
- **Bar bawah jadi 3 tab: Dashboard · Login · Setelan** (saran Maulana — tab 6 sebelumnya sempit sampai label DASHBOARD terpotong dua baris). Mode, Band, dan SMS berubah jadi sub-layar Dashboard: tetap dibuka dari chip aksi cepat di Dashboard dan tampil sebagai layar penuh yang sama persis (isi tidak berubah), hanya sorotan bar-nya ikut Dashboard — pola yang sama seperti Antena & Perangkat selama ini. Tombol kembali dari ketiganya tetap ke Dashboard.

## v2.2.1 (2026-10-21)
- Fix header Dashboard: label BATERAI dan nilainya kini satu unit rata-kanan yang sejajar (tepi kanan selalu berimpit) dan bloknya sejajar vertikal-tengah dengan judul — sebelumnya lokasi tulisan baterai tidak sinkron.

## v2.2.0 (2026-10-21)
- **Tab LOGIN sendiri** (permintaan Maulana, sesuai mockup v5): form koneksi (alamat router, username, password), kartu sesi, LOGIN/LOGOUT, SIMPAN & TES KONEKSI, dan BUKA WEBUI pindah dari Setelan ke tab LOGIN di bar bawah (6 tab: Dashboard, Band, Mode, SMS, Login, Setelan). Perilaku persis sama, hanya tempatnya yang pindah.
- Setelan jadi ramping: Pengaturan Router (WiFi/DHCP/USSD/APN/Diagnostik), Interval Perbarui Dashboard, dan Tentang (versi dibaca dari paket terpasang).

## v2.1.9 (2026-10-21)
- **Arah Antena** (sesuai mockup yang disetujui Maulana): layar kompas sinyal live meniru halaman antennapointing WebUI mod — RSRP/SINR/RSRQ raksasa berwarna mengikuti mutu, RSSI + RAT/band, kartu nilai terbaik sesi (RSRP+SINR, bisa direset), dan grid info lengkap (PLMN/operator, eNB/cell, LAC & Cell ID dec/hex, frekuensi DL/UL, bandwidth, EARFCN, PCI). Masuk dari tombol Antena di Dashboard atau tombol di Perangkat. Polling sinyal 1 detik via jalur ringan (device/signal mentah); data sel/frekuensi dari add_param.xml tiap 5 detik. Mode 3G menampilkan RSCP/Ec-No.

## v2.1.8 — 2026-10-21
BAND KETEMU (dari bongkaran paket WebUI Mod1.21 yang dikirim Maulana): WebUI mod menjalankan daemon add_param yang mengambil band/EARFCN via AT^HFREQINFO dan menyajikannya di /config/deviceinformation/add_param.xml (tanpa login, aktif default). signal() kini membaca file itu bila band belum diketahui — dashboard menampilkan band asli (mis. B1). USSD: varian kirim ke-6 (konten hex UCS-2, mengikuti petunjuk ussd_encode=1 di config mod). Diagnostik: pemeriksaan add_param.xml.

## v2.1.7 — 2026-10-21
USSD: kirim multi-varian (5 bentuk body berurutan sampai modem menerima; hanya kode penolakan yang dicoba ulang, varian pemenang dilaporkan di layar). Diagnostik: pemeriksaan baru "Bedah JS WebUI" — mengambil halaman + file JS WebUI dari router dan mengekstrak semua alamat api/ serta cuplikan berkode band/earfcn, untuk menemukan jalur data yang dipakai WebUI mod.

## v2.1.6 — 2026-10-21
Baterai: dashboard kini membaca BatteryPercent (temuan dari laporan diagnostik: BatteryLevel adalah skala bar 0–4, selama ini tampil "4%" padahal baterai 100%). Diagnostik: pemeriksaan baru "Varian sinyal" — 8 bentuk permintaan ke device/signal & net/cell-info (Referer WebUI, XHR, POST, tanpa sesi, token segar) untuk memburu respons kaya berisi band/EARFCN/txpower seperti yang diterima WebUI mod; error di dump kini menyertakan kode numerik; status USSD membaca kunci result.

## v2.1.5 — 2026-10-21
Menu Diagnostik ala NetAver DNS (permintaan Maulana): Setelan → Diagnostik menjalankan pemeriksaan 12 endpoint (jangkauan, sesi, info perangkat, sinyal, status, trafik, mode, SMS, perangkat, APN, USSD, data mentah) dengan checklist langsung di layar, lalu menyusun SATU laporan JSON (meta + snapshot + hasil cek + data mentah) yang bisa DISALIN / DIBAGIKAN / DISIMPAN ke Download (nama netaver-hilink-diagnostik-*.json). IMEI/serial/MAC disamarkan otomatis; kata sandi tidak pernah masuk laporan. Termasuk perbaikan v2.1.4: band dari XML mentah semua kedalaman + status USSD pra-kirim.

## v2.1.4 — 2026-10-21
Band: pencarian kini juga memindai XML mentah di semua kedalaman (parser biasa hanya membaca anak langsung; band/EARFCN yang bersarang kini ketemu) — elemen "band" persis saja yang dihitung (bukan bandwidth MHz, bukan mask kunci). USSD: status modem dibaca sebelum kirim; diagnostik kini juga mengambil ussd/status & ussd/get; penjelasan error 111019 diperbarui (sesi menggantung → restart router).

## v2.1.3 — 2026-10-21
Diagnostik: layar baru Setelan → Pengaturan Router → Diagnostik Router (ambil XML mentah endpoint engineering router, pub+login, bisa disalin untuk dianalisis). Pembacaan sinyal kini mencoba sesi login dulu (data engineering kadang hanya tampil setelah login) sebelum fallback publik. Error USSD 111019 kini berpenjelasan (kode ditolak jaringan — untuk SIM Tri gunakan kode Tri).

## v2.1.2 — 2026-10-21
Deteksi band diperluas (di v2.1.1 band tidak muncul di routerya): nilai band kini digali dari elemen band dalam format apa pun ("LTE Band 3" dsb.), dari EARFCN downlink (dihitung ke band via tabel 3GPP), dan dicoba juga endpoint cell-info alternatif (/api/device/cell-info) selain /api/net/cell-info. Fallback mask kunci satu-band tetap ada.

## v2.1.1 — 2026-10-21
Home: (1) keadaan tanpa sinyal kini menampilkan "No Signal" MERAH pada angka sinyal utama + semua kotak radio (RSRQ/SINR/RSSI/PCI/Cell ID) dan bar sinyal merah redup — tidak lagi kosong/dash. (2) Band yang sedang dipakai tampil di judul hero (mis. "Operator · 4G LTE · B3") dari laporan sinyal router, atau dari mask kunci band bila tepat satu band. (3) Nilai sinyal dinormalisasi: satuan bawaan firmware (mis. "-73dBm") dibuang agar satuan tidak dobel. Error USSD 111013 kini berpenjelasan (modem tidak terhubung/tidak ada sinyal).

## v2.1.0 — 2026-10-21
Pengaturan Router native (Setelan → Pengaturan Router), tanpa WebUI: (1) WiFi — ganti nama SSID, password, mode keamanan (WPA2/WPA-campur/Terbuka), sembunyikan SSID, matikan/nyalakan radio; penulisan read-modify-write dari nilai yang dibaca router. (2) LAN & DHCP — IP router (app otomatis mengikuti alamat baru), subnet, rentang DHCP, lama sewa. (3) USSD — kirim kode dial (*123# dll.), balasan operator ditunggu & ditampilkan, sesi menu bisa diteruskan; balasan hex UCS-2 didekode otomatis. (4) Profil APN — daftar profil, buat/ubah/hapus, jadikan aktif (format wire mengikuti implementasi HiLink teruji di E5577Cs-321).

## v2.0.4 — 2026-10-21
SMS: isi pesan kini bisa diseleksi (tekan-tahan untuk memilih teks, mis. kode OTP) + tombol Salin untuk menyalin seluruh isi pesan. SMS terkonfirmasi jalan di device (20 pesan, tandai-dibaca otomatis).

## v2.0.3 — 2026-10-21
Login TERBUKTI bekerja di device (WebUI mod). Perbaikan label: kode tipe jaringan 19 pada E5577 = LTE (sebelumnya salah terbaca HSPA+) + label kini diputuskan juga dari metrik sinyal (RSRP/RSRQ hanya ada di LTE). Kartu traffic: "Sesi ini" kini menampilkan penghitung koneksi berjalan (Current), dan total kumulatif dipindah ke baris keterangan.

## v2.0.2 — 2026-10-21
AKAR MASALAH LOGIN DITEMUKAN: skema hash password Huawei mengenkode hasil SHA-256 sebagai TEKS HEX ke base64 (persis login.js WebUI asli) — v1.0/v2.0/v2.0.1 mengenkode byte mentahnya, jadi router selalu menjawab password salah. HashKit baru meniru skema asli persis dan lolos tes vektor referensi. Cookie jar + token segar + jeda aman dari v2.0.1 tetap dipertahankan.

## v2.0.1 — 2026-10-21
Perbaikan login (temuan tes device v2.0): cookie jar penuh (WebUI mod mengikat token ke cookie sesi — tanpa cookie, login selalu ditolak walau password benar), token diambil segar tepat sebelum tiap percobaan login, sesi dari badan respon login diadopsi, dan jeda aman anti-bakar percobaan (kegagalan login tidak diulang dashboard secara membabi buta; tombol LOGIN manual melewati jeda app tapi tetap menghormati blokir router).

## v2.0 — 2026-10-21
Tulis ulang Kotlin: sesi tunggal, password terenkripsi Keystore, penjaga LAN-only, fungsi lengkap + kontrol perangkat (matikan/reset pabrik/hapus statistik), pindai operator, error Bahasa Indonesia.

# Changelog NetAver HiLink

## v1.0 (2026-10-19)
- Rilis awal: Dashboard (status, sinyal RSSI/RSRP/RSRQ/SINR, data harian/
  bulanan, info device, auto-refresh 10 dtk), Band lock LTE (10 band +
  network mode Auto/4G/3G), SMS (inbox, kirim, hapus, tandai dibaca),
  Settings (IP/username/password, test login, reboot router).
- HiLink API client dari nol: session + token, login SHA256, XML defensif.

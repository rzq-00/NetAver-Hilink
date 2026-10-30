# NetAver HiLink — paket share (TANPA keystore)

Paket ini TIDAK berisi debug.keystore dan sertifikat signing asli
(diganti placeholder <CERT_SHA256_KEYSTORE_SENDIRI>).

Untuk build:
1. Buat keystore sendiri: keytool -genkeypair -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
2. Ganti <CERT_SHA256_KEYSTORE_SENDIRI> di build.sh dengan SHA-256 sertifikat barumu (build.sh memverifikasi ini).
3. Jalankan ./build.sh (butuh JDK 17, kotlinc, Android SDK build-tools 34).

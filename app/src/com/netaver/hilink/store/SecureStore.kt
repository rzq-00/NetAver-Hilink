package com.netaver.hilink.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Router-password storage encrypted with an Android Keystore AES-GCM key.
 * The key never leaves the Keystore; SharedPreferences only hold ciphertext.
 * If the Keystore is unavailable, encryption reports failure and the caller
 * keeps the password in memory only — plaintext is never persisted.
 */
object SecureStore {
    private const val ALIAS = "netaver_hilink_pw"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    /** Returns Base64(iv|ciphertext), or null when encryption is unavailable. */
    fun encrypt(plain: String): String? {
        return try {
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, key())
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            val blob = c.iv + ct
            Base64.encodeToString(blob, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    fun decrypt(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            val blob = Base64.decode(encoded, Base64.DEFAULT)
            val iv = blob.copyOfRange(0, 12)
            val ct = blob.copyOfRange(12, blob.size)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }
}

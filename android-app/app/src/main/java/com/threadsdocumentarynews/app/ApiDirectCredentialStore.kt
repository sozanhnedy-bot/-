package com.threadsdocumentarynews.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the user-supplied API Direct key encrypted with a per-install Android Keystore key. */
object ApiDirectCredentialStore {
    private const val KEY_ALIAS = "world_news_apidirect_aes_gcm"
    private const val PREFS = "world_news_secure_provider_credentials"
    private const val PREF_CIPHERTEXT = "apidirect_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @JvmStatic
    fun hasKey(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(PREF_CIPHERTEXT)

    @JvmStatic
    fun save(context: Context, rawKey: String) {
        val key = rawKey.trim()
        require(key.length in 12..512 && key.none { it == '\n' || it == '\r' }) {
            "مفتاح ApiDirect غير صالح أو فارغ."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CIPHERTEXT, encoded)
            .apply()
    }

    @JvmStatic
    fun read(context: Context): String? {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = preferences.getString(PREF_CIPHERTEXT, null) ?: return null
        try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = 12
            if (combined.size <= ivLength) throw IllegalStateException("بيانات مفتاح ApiDirect المحفوظة غير صالحة.")
            val iv = combined.copyOfRange(0, ivLength)
            val encrypted = combined.copyOfRange(ivLength, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException("تعذر فك تشفير مفتاح ApiDirect المحفوظ. امسحه وأدخله مرة أخرى.", error)
        }
    }

    @JvmStatic
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_CIPHERTEXT)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

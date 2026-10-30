package com.goreecloud.homeassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES/GCM envelope. No tokens in logs, resources, repo or backups.
 * If device keys are reset, setup is required again rather than using an insecure fallback.
 */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("ha_secure_config_v1", Context.MODE_PRIVATE)
    private val alias = "goreecloud_ha_token_aes_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun save(connection: HaConnection) {
        val plaintext = JSONObject()
            .put("url", EndpointRules.normalize(connection.baseUrl))
            .put("token", connection.token.trim())
            .toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString("connection", encoded).commit()) {
            "Unable to save encrypted connection."
        }
    }

    fun load(): HaConnection? {
        val stored = preferences.getString("connection", null) ?: return null
        val encrypted = Base64.decode(stored, Base64.NO_WRAP)
        require(encrypted.size > 28) { "Invalid secure configuration." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        val plain = cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
        val json = JSONObject(String(plain, StandardCharsets.UTF_8))
        return HaConnection(EndpointRules.normalize(json.getString("url")), json.getString("token"))
    }

    fun clear() {
        check(preferences.edit().remove("connection").commit()) { "Could not clear connection." }
    }
}

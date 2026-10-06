package eu.kanade.tachiyomi.data.discord

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM over the Android Keystore for the Discord RPC token.
 *
 * The token used to sit in plain SharedPreferences (only excluded from backups), while the entry
 * dialog promised device-local storage. Ciphertext carries a marker prefix so encrypted and
 * legacy plain values coexist during the silent migration performed by
 * [EncryptedTokenPreference].
 *
 * Degradation policy: when the Keystore is unavailable (broken firmware, stripped devices)
 * encryption falls back to the previous plain storage instead of bricking the feature, and
 * [decrypt] treats unmarked values as legacy plaintext.
 */
class DiscordTokenCipher(
    private val keyLoader: () -> SecretKey? = ::loadAndroidKeystoreKey,
) {

    fun encrypt(plaintext: String): String {
        val key = runCatching { keyLoader() }.getOrNull()
        if (key == null) {
            logcat(LogPriority.WARN) { "Discord token keystore unavailable, storing without encryption" }
            return plaintext
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val payload = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            MARKER + Base64.getEncoder().encodeToString(payload)
        }.getOrElse { error ->
            logcat(LogPriority.WARN, error) { "Discord token encryption failed, storing without it" }
            plaintext
        }
    }

    fun decrypt(stored: String): String? {
        if (!stored.startsWith(MARKER)) return stored
        val key = runCatching { keyLoader() }.getOrNull() ?: return null
        return runCatching {
            val payload = runCatching {
                Base64.getDecoder().decode(stored.substring(MARKER.length))
            }.getOrNull() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH))
            String(cipher.doFinal(payload, IV_LENGTH, payload.size - IV_LENGTH), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        const val MARKER = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ALIAS = "tadami_discord_rpc_token"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128

        private fun loadAndroidKeystoreKey(): SecretKey? {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

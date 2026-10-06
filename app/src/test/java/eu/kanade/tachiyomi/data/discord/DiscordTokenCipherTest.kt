package eu.kanade.tachiyomi.data.discord

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import javax.crypto.KeyGenerator

class DiscordTokenCipherTest {

    private fun cipher() = DiscordTokenCipher(
        keyLoader = { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() },
    )

    @Test
    fun `round trip restores the plaintext and marks the ciphertext`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val crypto = DiscordTokenCipher(keyLoader = { key })

        val stored = crypto.encrypt("super.secret.token")

        stored shouldStartWith DiscordTokenCipher.MARKER
        crypto.decrypt(stored) shouldBe "super.secret.token"
    }

    @Test
    fun `legacy plaintext decrypts as-is and a wrong key yields null`() {
        val crypto = cipher()

        crypto.decrypt("legacy-plain-token") shouldBe "legacy-plain-token"

        val keyA = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val keyB = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val stored = DiscordTokenCipher(keyLoader = { keyA }).encrypt("token")
        DiscordTokenCipher(keyLoader = { keyB }).decrypt(stored) shouldBe null
    }

    @Test
    fun `missing keystore degrades to plain storage instead of failing`() {
        val crypto = DiscordTokenCipher(keyLoader = { null })

        crypto.encrypt("token") shouldBe "token"
    }
}

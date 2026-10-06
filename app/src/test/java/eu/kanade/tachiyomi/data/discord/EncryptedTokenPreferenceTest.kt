package eu.kanade.tachiyomi.data.discord

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import javax.crypto.KeyGenerator

class EncryptedTokenPreferenceTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = DiscordTokenCipher(keyLoader = { key })

    @Test
    fun `set stores ciphertext and get returns plaintext`() {
        val backing = FakeStringPreference()
        val preference = EncryptedTokenPreference(backing, cipher)

        preference.set("my.token.value")

        backing.value shouldStartWith DiscordTokenCipher.MARKER
        preference.get() shouldBe "my.token.value"
    }

    @Test
    fun `legacy plaintext migrates silently on first read`() {
        val backing = FakeStringPreference("legacy-plain-token")
        val preference = EncryptedTokenPreference(backing, cipher)

        preference.get() shouldBe "legacy-plain-token"

        backing.value shouldStartWith DiscordTokenCipher.MARKER
        preference.get() shouldBe "legacy-plain-token"
    }

    @Test
    fun `changes flow emits plaintext and empty clears`() = runTest {
        val backing = FakeStringPreference()
        val preference = EncryptedTokenPreference(backing, cipher)

        preference.set("another.token")
        preference.changes().first() shouldBe "another.token"

        preference.set("")
        preference.get() shouldBe ""
        preference.isSet() shouldBe false
    }

    private class FakeStringPreference(initial: String = "") : Preference<String> {
        var value: String = initial
        private val flow = MutableStateFlow(value)

        override fun key(): String = "test-key"
        override fun get(): String = value
        override fun set(value: String) {
            this.value = value
            flow.value = value
        }

        override fun isSet(): Boolean = value.isNotEmpty()
        override fun delete() = set("")
        override fun defaultValue(): String = ""
        override fun changes(): Flow<String> = flow
        override fun stateIn(
            scope: CoroutineScope,
        ): StateFlow<String> = flow.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, value)
    }
}

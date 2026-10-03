package eu.kanade.tachiyomi.data.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DiscordPreferences(
    private val preferenceStore: PreferenceStore,
    private val cipher: DiscordTokenCipher = DiscordTokenCipher(),
) {

    fun enabled() = preferenceStore.getBoolean(
        Preference.privateKey("pref_discord_presence_enabled"),
        false,
    )

    /**
     * The RPC token, encrypted at rest via [DiscordTokenCipher]. Readers (gateway, presence
     * manager) keep seeing plaintext; the ciphertext never leaves this wrapper.
     */
    fun token(): Preference<String> = EncryptedTokenPreference(
        backing = preferenceStore.getString(
            Preference.privateKey("pref_discord_presence_token"),
            "",
        ),
        cipher = cipher,
    )
}

/**
 * Preference view that stores the Discord token as Keystore-encrypted ciphertext.
 *
 * Self-migrating: a value without the cipher marker is a legacy plaintext token, which is
 * re-encrypted in place on the first read and returned as-is, so existing setups keep working
 * without re-entering the token.
 */
internal class EncryptedTokenPreference(
    private val backing: Preference<String>,
    private val cipher: DiscordTokenCipher,
) : Preference<String> {

    override fun key(): String = backing.key()

    override fun get(): String {
        val stored = backing.get()
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(DiscordTokenCipher.MARKER)) {
            // Legacy plaintext from before at-rest encryption: migrate silently.
            backing.set(cipher.encrypt(stored))
            return stored
        }
        return cipher.decrypt(stored) ?: ""
    }

    override fun set(value: String) {
        backing.set(if (value.isEmpty()) "" else cipher.encrypt(value))
    }

    override fun isSet(): Boolean = backing.get().isNotEmpty()

    override fun delete() = backing.delete()

    override fun defaultValue(): String = ""

    override fun changes(): Flow<String> = backing.changes().map(::decode)

    override fun stateIn(scope: CoroutineScope): StateFlow<String> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())

    private fun decode(stored: String): String {
        return when {
            stored.isEmpty() -> ""
            stored.startsWith(DiscordTokenCipher.MARKER) -> cipher.decrypt(stored) ?: ""
            else -> stored
        }
    }
}

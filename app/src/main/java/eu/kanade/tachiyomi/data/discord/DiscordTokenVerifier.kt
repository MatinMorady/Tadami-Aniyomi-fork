package eu.kanade.tachiyomi.data.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pre-save validation of a Discord token against the official REST API, so an invalid token is
 * rejected in the dialog instead of surfacing later as a disconnected presence.
 */
class DiscordTokenVerifier(
    private val client: OkHttpClient,
    private val verifyUrl: String = DEFAULT_VERIFY_URL,
) {

    sealed interface Result {
        data class Valid(val username: String) : Result
        data object Invalid : Result
        data object NetworkError : Result
    }

    suspend fun verify(token: String): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(verifyUrl)
            .header("Authorization", token)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val username = runCatching {
                            Json.parseToJsonElement(response.body.string())
                                .jsonObject["username"]
                                ?.jsonPrimitive
                                ?.content
                        }.getOrNull()
                        Result.Valid(username.orEmpty())
                    }
                    response.code == 429 -> Result.NetworkError
                    response.code == 401 || response.code == 403 -> Result.Invalid
                    else -> Result.Invalid
                }
            }
        }.getOrElse { Result.NetworkError }
    }

    companion object {
        private const val DEFAULT_VERIFY_URL = "https://discord.com/api/v10/users/@me"

        /** Structural shape of a Discord user token: three base64url segments (id.timestamp.hmac). */
        private val TOKEN_FORMAT = Regex("^[A-Za-z\\d_-]{20,32}\\.[A-Za-z\\d_-]{4,10}\\.[A-Za-z\\d_-]{20,}$")

        fun looksLikeToken(token: String): Boolean = TOKEN_FORMAT.matches(token)
    }
}

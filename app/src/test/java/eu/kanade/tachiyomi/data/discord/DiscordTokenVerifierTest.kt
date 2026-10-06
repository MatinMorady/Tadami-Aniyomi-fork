package eu.kanade.tachiyomi.data.discord

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class DiscordTokenVerifierTest {

    @Test
    fun `successful lookup reports the account name`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"username":"reader"}"""))
        val verifier = DiscordTokenVerifier(OkHttpClient(), server.url("/users/@me").toString())

        verifier.verify("tok") shouldBe DiscordTokenVerifier.Result.Valid("reader")

        server.takeRequest().getHeader("Authorization") shouldBe "tok"
        server.shutdown()
    }

    @Test
    fun `auth failures map to Invalid and outages to NetworkError`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(401))
        val verifier = DiscordTokenVerifier(OkHttpClient(), server.url("/users/@me").toString())

        verifier.verify("bad") shouldBe DiscordTokenVerifier.Result.Invalid
        server.shutdown()

        val dead = DiscordTokenVerifier(OkHttpClient(), server.url("/users/@me").toString())
        dead.verify("any") shouldBe DiscordTokenVerifier.Result.NetworkError
    }

    @Test
    fun `format gate accepts token shape and rejects prose`() {
        DiscordTokenVerifier.looksLikeToken("NDk2NTA0NTQyMjI4MzI4NDQ4.GtXoJQ.6V1JvY4pZpJpX4pZpJpX4pZpJpX") shouldBe true
        DiscordTokenVerifier.looksLikeToken("not a token at all") shouldBe false
        DiscordTokenVerifier.looksLikeToken("") shouldBe false
    }
}

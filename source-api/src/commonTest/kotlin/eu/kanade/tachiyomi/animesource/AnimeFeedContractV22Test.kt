package eu.kanade.tachiyomi.animesource

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeFeedContractV22Test {

    private class FakeInstrumented : AnimeFeedLoginInstrumentationSource {
        override fun sessionInstrumentationJs(): String = "(function(){window.__x=1;})()"

        override fun extraSessionCookieOrigins(): List<String> = listOf("https://api.example.invalid")

        override fun logoutCookieOrigins(): List<String> = listOf("https://auth2.example.invalid")
    }

    private class BareInstrumented : AnimeFeedLoginInstrumentationSource {
        override fun sessionInstrumentationJs(): String? = null
        // Defaults of the new interface stay usable: no instrumentation, generic origins.
    }

    private class FakeResolver : AnimeFeedVideoResolverSource {
        override suspend fun resolveVideoUrl(itemId: String, hd: Boolean): String? =
            "https://cdn.example.invalid/$itemId/${if (hd) "hd" else "sd"}.mp4"
    }

    @Test
    fun instrumentationCapabilityIsInstanceofDetectedAndIsolated() {
        val source: Any = FakeInstrumented()
        assertTrue(source is AnimeFeedLoginInstrumentationSource)
        assertFalse(source is AnimeFeedWebLoginSource)
        assertFalse(source is AnimeFeedVideoResolverSource)
    }

    @Test
    fun aNullInstrumentationAnswerKeepsTheGenericFallbackContract() {
        val source = BareInstrumented()
        assertNull(source.sessionInstrumentationJs())
        assertTrue(source.extraSessionCookieOrigins().isEmpty())
        assertTrue(source.logoutCookieOrigins().isEmpty())
    }

    @Test
    fun resolverCapabilityIsInstanceofDetectedAndIsolated() {
        val source: Any = FakeResolver()
        assertTrue(source is AnimeFeedVideoResolverSource)
        assertFalse(source is AnimeFeedLoginInstrumentationSource)
    }
}

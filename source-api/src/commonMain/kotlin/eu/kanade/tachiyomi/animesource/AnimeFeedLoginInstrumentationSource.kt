package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (feed contract v22): a web-login source that supplies its OWN session
 * instrumentation for the host's login WebView, so the host never hardcodes service-specific
 * scraping (the pre-v22 in-host instrumentation was RedGIFs-shaped and silently didn't fit
 * any other source).
 *
 * The host detects support with `source is AnimeFeedLoginInstrumentationSource` (instanceof) —
 * the same marker-interface pattern as the earlier capabilities; no default members are added
 * to existing interfaces, so plugins compiled against older source-api versions are untouched.
 * A source implements this together with [AnimeFeedWebLoginSource]; every answer is optional
 * (null/empty = the host falls back to the generic cookie/localStorage dump).
 */
interface AnimeFeedLoginInstrumentationSource {

    /**
     * JavaScript snippet the host evaluates on the service's SPA. Must be idempotent (install
     * a `window.__x`-style guard) and record the SPA's live credentials into window globals
     * or DOM storage, which the host's storage dump then hands back through
     * [AnimeFeedWebLoginSource.importWebSession]. Null/empty = plain dump, no instrumentation.
     */
    fun sessionInstrumentationJs(): String?

    /**
     * Extra cookie origins to include in the session dump beyond the page origins the WebView
     * actually visited (e.g. `api.` siblings the SPA calls without navigating there).
     */
    fun extraSessionCookieOrigins(): List<String> = emptyList()

    /**
     * Origins whose cookies the host must purge on logout. Empty = the host uses the generic
     * same-site purge derived from the web-login URL.
     */
    fun logoutCookieOrigins(): List<String> = emptyList()
}

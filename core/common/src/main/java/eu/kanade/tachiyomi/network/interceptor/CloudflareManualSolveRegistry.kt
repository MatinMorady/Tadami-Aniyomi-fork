package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Records hosts whose Cloudflare challenge the hidden WebView solve could not finish.
 *
 * A managed challenge sometimes needs a human (or a device where the headless solve cannot
 * render), and the only reliable way through is passing it once in a visible WebView: the earned
 * cf_clearance lives in the shared cookie store and clears the following OkHttp fetches. The
 * catalogue error card reads [isPending] to offer exactly that detour, so a blocked source stops
 * being a dead end.
 */
object CloudflareManualSolveRegistry {

    private const val TTL_MS = 10 * 60 * 1000L

    private val pending = ConcurrentHashMap<String, Long>()

    fun request(host: String) {
        pending[host] = System.currentTimeMillis()
    }

    fun isPending(host: String): Boolean {
        val requestedAt = pending[host] ?: return false
        if (System.currentTimeMillis() - requestedAt > TTL_MS) {
            pending.remove(host, requestedAt)
            return false
        }
        return true
    }

    fun clearAll() {
        pending.clear()
    }
}

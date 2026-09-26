package eu.kanade.tachiyomi.extension.novel.runtime

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.attachWebViewToWindow
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes plugin fetches through a WebView's own network stack for hosts whose Cloudflare
 * challenge the OkHttp path cannot clear.
 *
 * Cloudflare trusts the WebView fingerprint and challenges OkHttp's, and a trusted WebView
 * navigation is often let through *without* a challenge - which means no cf_clearance cookie is
 * ever issued for OkHttp to replay. Running the very same request as a same-origin `fetch()`
 * inside a WebView on the target origin reuses the browser fingerprint and the shared cookie
 * jar, so protected endpoints (search, ranking, chapter POSTs) answer normally.
 *
 * The bridge keeps one hidden WebView per process, navigates it to the target origin once and
 * serializes requests through it. Response bodies travel back through a JavascriptInterface in
 * chunks to stay under the binder transaction limit.
 */
internal object NovelWebViewFetchBridge {

    private const val TAG = "NovelWebViewBridge"
    private const val PREPARE_TIMEOUT_MS = 5_000L
    private const val NAVIGATE_TIMEOUT_MS = 20_000L
    private const val FETCH_TIMEOUT_MS = 45_000L
    private const val CHUNK_SIZE = 256 * 1024

    // Lazy: unit tests touch the pure helpers without an Android looper present.
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val bridgeLock = Any()
    private val pendingCalls = ConcurrentHashMap<String, BridgeCall>()
    private val json = Json { ignoreUnknownKeys = true }

    private var webView: WebView? = null

    @Volatile
    private var loadedOrigin: String? = null

    @Volatile
    private var navigationLatch: CountDownLatch? = null

    /**
     * Last successfully fetched page URL per bridge, used as the document for non-GET calls.
     *
     * A browser issues plugin POSTs from the page the plugin just read (e.g. the series page), so
     * the request carries that page as Referer and its DOM as context. Some WordPress ajax handlers
     * and WAF rules expect exactly that; parking the bridge document on the origin root made the
     * same POST answer "0". Re-navigating before a POST reproduces the browser context.
     */
    @Volatile
    private var lastPageUrl: String? = null

    @Volatile
    private var loadedPageUrl: String? = null

    private class BridgeCall {
        val payload = StringBuilder()
        val finished = CountDownLatch(1)

        @Volatile
        var error: String? = null
    }

    @Serializable
    private data class BridgePayload(
        val status: Int,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    )

    fun fetchBlocking(
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): NovelJsRuntimeFactory.JsFetchResponse? {
        val targetOrigin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: return null
        val isGet = options.method.equals("GET", ignoreCase = true)
        synchronized(bridgeLock) {
            val token = UUID.randomUUID().toString()
            val call = BridgeCall()
            pendingCalls[token] = call
            try {
                val documentUrl = if (isGet) {
                    "$targetOrigin/"
                } else {
                    lastPageUrl?.takeIf { page -> page == targetOrigin || page.startsWith("$targetOrigin/") }
                        ?: "$targetOrigin/"
                }
                if (!prepareOnMain(targetOrigin, documentUrl)) return null
                if (loadedOrigin != targetOrigin) return null
                val script = FETCH_SCRIPT.replace("__CFG__", buildBridgeConfigJson(token, url, options))
                handler.post { webView?.evaluateJavascript(script, null) }
                if (!call.finished.await(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    logcat(priority = LogPriority.WARN, tag = TAG) { "fetch timed out url=$url" }
                    return null
                }
                call.error?.let { error ->
                    logcat(priority = LogPriority.WARN, tag = TAG) { "fetch failed url=$url error=$error" }
                    return null
                }
                val payload = runCatching {
                    json.decodeFromString(BridgePayload.serializer(), call.payload.toString())
                }.getOrNull()
                if (payload == null) {
                    logcat(priority = LogPriority.WARN, tag = TAG) { "fetch returned unparsable payload url=$url" }
                    return null
                }
                logcat(priority = LogPriority.DEBUG, tag = TAG) {
                    "fetch via webview status=${payload.status} bytes=${payload.body?.length ?: 0} url=$url"
                }
                if (isGet && payload.status in 200..299) {
                    lastPageUrl = url
                }
                return NovelJsRuntimeFactory.JsFetchResponse(
                    status = payload.status,
                    url = payload.url,
                    headers = payload.headers,
                    body = payload.body,
                    bodyBase64 = null,
                )
            } finally {
                pendingCalls.remove(token)
            }
        }
    }

    /** Creates the hidden WebView when needed and navigates it to [documentUrl]. Main-thread safe. */
    private fun prepareOnMain(targetOrigin: String, documentUrl: String): Boolean {
        val prepared = CountDownLatch(1)
        var prepareError: String? = null
        handler.post {
            prepareError = runCatching {
                val view = ensureWebView()
                if (loadedPageUrl != documentUrl) {
                    navigationLatch = CountDownLatch(1)
                    view.loadUrl(documentUrl)
                }
            }.exceptionOrNull()?.message
            prepared.countDown()
        }
        if (!prepared.await(PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            logcat(priority = LogPriority.WARN, tag = TAG) { "webview prepare timed out origin=$targetOrigin" }
            return false
        }
        if (prepareError != null) {
            logcat(priority = LogPriority.WARN, tag = TAG) { "webview prepare failed: $prepareError" }
            return false
        }
        if (loadedOrigin != targetOrigin) {
            navigationLatch?.await(NAVIGATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        return loadedOrigin == targetOrigin
    }

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val context = Injekt.get<Application>()
        val view = object : WebView(context) {
            // The bridge overlay must never swallow touches meant for the activity below it.
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false
        }
        view.setDefaultSettings()
        view.settings.userAgentString = Injekt.get<NetworkHelper>().pluginUserAgentProvider()
        view.addJavascriptInterface(BridgeInterface, "TadamiBridge")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loadedPageUrl = url
                loadedOrigin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                navigationLatch?.countDown()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    navigationLatch?.countDown()
                }
            }
        }
        attachWebViewToWindow(view)
        webView = view
        return view
    }

    private object BridgeInterface {
        @JavascriptInterface
        fun chunk(token: String, data: String) {
            pendingCalls[token]?.payload?.append(data)
        }

        @JavascriptInterface
        fun done(token: String) {
            pendingCalls[token]?.finished?.countDown()
        }

        @JavascriptInterface
        fun error(token: String, message: String) {
            pendingCalls[token]?.let { call ->
                call.error = message
                call.finished.countDown()
            }
        }
    }

    internal fun buildBridgeConfigJson(
        token: String,
        url: String,
        options: NovelJsRuntimeFactory.JsFetchRequest,
    ): String = buildJsonObject {
        put("token", token)
        put("url", url)
        put("method", options.method.uppercase(Locale.US))
        put("chunk", CHUNK_SIZE)
        putJsonObject("headers") {
            filterBridgeHeaders(options.headers).forEach { (name, value) -> put(name, value) }
        }
        put(
            "bodyKind",
            when (options.bodyType) {
                NovelJsRuntimeFactory.BodyType.Text -> "text"
                NovelJsRuntimeFactory.BodyType.Form -> "form"
                NovelJsRuntimeFactory.BodyType.None -> "none"
            },
        )
        options.body?.let { put("body", it) }
        options.formEntries?.let { entries ->
            putJsonArray("form") {
                entries.forEach { entry ->
                    add(
                        buildJsonArray {
                            add(JsonPrimitive(entry.key))
                            add(JsonPrimitive(entry.value))
                        },
                    )
                }
            }
        }
    }.toString()

    /** Browser fetch() forbids setting transport headers; sending them would throw a TypeError. */
    internal fun filterBridgeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.filterKeys { name ->
            val lower = name.lowercase(Locale.US)
            lower !in FORBIDDEN_BRIDGE_HEADERS && !lower.startsWith("proxy-")
        }
    }

    private val FORBIDDEN_BRIDGE_HEADERS = setOf(
        "accept-charset",
        "accept-encoding",
        "access-control-request-headers",
        "access-control-request-method",
        "connection",
        "content-length",
        "cookie",
        "cookie2",
        "date",
        "dnt",
        "expect",
        "host",
        "keep-alive",
        "origin",
        "proxy-",
        "referer",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "user-agent",
        "via",
    )

    private val FETCH_SCRIPT = """
        (function(cfg){
          var B = window.TadamiBridge;
          if (!B) { return; }
          var opts = { method: cfg.method, headers: cfg.headers, credentials: 'same-origin', redirect: 'follow' };
          if (cfg.bodyKind === 'text' && cfg.body != null) { opts.body = cfg.body; }
          if (cfg.bodyKind === 'form' && cfg.form) {
            // Encode by hand and pin the Content-Type: a browser-native URLSearchParams would do
            // the same, but an explicit body leaves no room for engine-specific surprises and
            // WordPress only fills its POST map for urlencoded bodies.
            opts.body = cfg.form.map(function(pair){ return encodeURIComponent(pair[0]) + "=" + encodeURIComponent(pair[1]); }).join("&");
            opts.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
          }
          fetch(cfg.url, opts).then(function(response){
            return response.text().then(function(text){
              var headers = {};
              response.headers.forEach(function(value, key){ headers[key] = value; });
              var payload = JSON.stringify({ status: response.status, url: response.url, headers: headers, body: text });
              for (var i = 0; i < payload.length; i += cfg.chunk) {
                B.chunk(cfg.token, payload.substr(i, cfg.chunk));
              }
              B.done(cfg.token);
            });
          }).catch(function(error){ B.error(cfg.token, String(error)); });
        })(__CFG__);
    """.trimIndent()
}

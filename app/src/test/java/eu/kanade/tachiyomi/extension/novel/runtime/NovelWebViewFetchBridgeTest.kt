package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelWebViewFetchBridgeTest {

    @Test
    fun `bridge headers drop transport headers forbidden by browser fetch`() {
        val filtered = NovelWebViewFetchBridge.filterBridgeHeaders(
            mapOf(
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to "https://www.novelupdates.com/",
                "Accept-Encoding" to "zstd, br, gzip",
                "User-Agent" to "Mozilla/5.0",
                "Proxy-Auth" to "x",
                "X-Custom" to "keep-me",
            ),
        )

        filtered shouldContainKey "Accept"
        filtered shouldContainKey "Content-Type"
        filtered shouldContainKey "X-Custom"
        filtered shouldNotContainKey "Referer"
        filtered shouldNotContainKey "Accept-Encoding"
        filtered shouldNotContainKey "User-Agent"
        filtered shouldNotContainKey "Proxy-Auth"
    }

    @Test
    fun `bridge config carries url method and form body for same-origin fetch`() {
        val config = NovelWebViewFetchBridge.buildBridgeConfigJson(
            token = "token-1",
            url = "https://www.novelupdates.com/wp-admin/admin-ajax.php",
            options = NovelJsRuntimeFactory.JsFetchRequest(
                method = "POST",
                headers = mapOf("Referer" to "https://www.novelupdates.com/"),
                bodyType = NovelJsRuntimeFactory.BodyType.Form,
                formEntries = listOf(
                    NovelJsRuntimeFactory.FormEntry(key = "action", value = "search"),
                ),
            ),
        )

        config shouldContain """"url":"https://www.novelupdates.com/wp-admin/admin-ajax.php""""
        config shouldContain """"method":"POST""""
        config shouldContain """"bodyKind":"form""""
        config shouldContain """"action","search""""
        config shouldNotContain "Referer"
    }
}

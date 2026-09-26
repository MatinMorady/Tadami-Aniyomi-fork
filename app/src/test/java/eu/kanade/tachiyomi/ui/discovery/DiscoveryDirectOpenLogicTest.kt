package eu.kanade.tachiyomi.ui.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryDirectOpenLogicTest {

    private fun suggestion(sourceId: Long?, sourceUrl: String?) = DiscoverySuggestion(
        id = 0,
        mediaType = DiscoveryMediaType.NOVEL,
        rowType = DiscoveryRowType.SOURCE,
        title = "Title",
        cleanTitle = "title",
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "p",
        score = 0.0,
        position = 0,
        createdAt = 0,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
    )

    @Test
    fun `full binding with installed source opens directly`() {
        decideDirectOpen(suggestion(sourceId = 7, sourceUrl = "/x")) { true } shouldBe
            DirectOpenDecision.Open(7, "/x")
    }

    @Test
    fun `missing sourceId falls back to sheet`() {
        decideDirectOpen(suggestion(sourceId = null, sourceUrl = "/x")) { true } shouldBe
            DirectOpenDecision.Sheet
    }

    @Test
    fun `missing sourceUrl falls back to sheet`() {
        decideDirectOpen(suggestion(sourceId = 7, sourceUrl = null)) { true } shouldBe
            DirectOpenDecision.Sheet
    }

    @Test
    fun `uninstalled source falls back to sheet`() {
        decideDirectOpen(suggestion(sourceId = 7, sourceUrl = "/x")) { false } shouldBe
            DirectOpenDecision.Sheet
    }

    @Test
    fun `blank sourceUrl falls back to sheet`() {
        decideDirectOpen(suggestion(sourceId = 7, sourceUrl = "  ")) { true } shouldBe
            DirectOpenDecision.Sheet
    }
}

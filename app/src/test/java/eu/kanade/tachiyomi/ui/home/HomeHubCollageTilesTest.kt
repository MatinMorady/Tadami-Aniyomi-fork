package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class HomeHubCollageTilesTest {

    private fun item(title: String, shownAt: Long? = null) = HomeHubDiscoveryItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        seedTitle = null,
        reasonPayload = null,
        provider = "test",
        rowType = DiscoveryRowType.TREND,
        mediaType = DiscoveryMediaType.NOVEL,
        shownAt = shownAt,
    )

    @Test
    fun `tiles are capped at five`() {
        val items = (1..12).map { item("title$it") }
        collageTiles(items, seed = 1).size shouldBe 5
    }

    @Test
    fun `tiles prefer unseen titles`() {
        val fresh = (1..4).map { item("fresh$it") }
        val shown = (1..4).map { item("shown$it", shownAt = 100L) }
        val tiles = collageTiles(fresh + shown, seed = 2)
        // 4 свежих + 1 добор из показанных: свежие не вытесняются показанными.
        tiles.take(4).all { it.shownAt == null } shouldBe true
    }

    @Test
    fun `at most one tile per franchise in the window`() {
        val franchise = listOf(item("naruto"), item("naruto shippuden"))
        val others = (1..6).map { item("solo$it") }
        val tiles = collageTiles(franchise + others, seed = 3)
        tiles.count { it.title.startsWith("naruto") } shouldBe 1
    }

    @Test
    fun `tiles are deterministic per seed`() {
        val items = (1..10).map { item("t$it", shownAt = if (it % 3 == 0) it * 10L else null) }
        collageTiles(items, seed = 4) shouldBe collageTiles(items, seed = 4)
    }

    @Test
    fun `small pools pass through without loss`() {
        val items = listOf(item("a"), item("b"), item("c"))
        collageTiles(items, seed = 5).toSet() shouldBe items.toSet()
    }
}

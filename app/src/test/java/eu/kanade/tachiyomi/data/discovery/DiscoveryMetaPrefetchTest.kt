package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.ui.discovery.prefetchDiscoveryMeta
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import java.io.IOException

class DiscoveryMetaPrefetchTest {

    private class FakeTrending : DiscoveryTrendingSource {
        val fetched = mutableListOf<String>()
        var failOn: String? = null

        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
            if (title == failOn) throw IOException("boom")
            fetched += title
            return null
        }
    }

    private fun suggestion(title: String) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.NOVEL,
        rowType = DiscoveryRowType.LIKE,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 0.0,
        position = 0L,
        createdAt = 0L,
    )

    @Test
    fun `prefetches only first N titles in order`() = runTest {
        val fake = FakeTrending()
        prefetchDiscoveryMeta((1..12).map { suggestion("T$it") }, DiscoveryMediaType.NOVEL, fake, limit = 8)
        fake.fetched shouldBe (1..8).map { "T$it" }
    }

    @Test
    fun `single failure does not stop the sweep`() = runTest {
        val fake = FakeTrending().apply { failOn = "T2" }
        prefetchDiscoveryMeta(
            listOf(suggestion("T1"), suggestion("T2"), suggestion("T3")),
            DiscoveryMediaType.NOVEL,
            fake,
        )
        fake.fetched shouldBe listOf("T1", "T3")
    }
}

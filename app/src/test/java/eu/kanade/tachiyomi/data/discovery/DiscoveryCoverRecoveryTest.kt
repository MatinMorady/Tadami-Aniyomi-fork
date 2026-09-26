package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryCoverRecoveryTest {

    private fun sugg(title: String, cover: String?) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.NOVEL,
        rowType = DiscoveryRowType.LIKE,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = cover,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 0.0,
        position = 0L,
        createdAt = 0L,
    )

    private class FakeTrending(
        private val covers: Map<String, String>,
        cancelOn: Set<String> = emptySet(),
    ) : DiscoveryTrendingSource {
        var calls = 0
        private val pendingCancels = cancelOn.toMutableSet()

        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
            releaseStatuses: Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
            calls++
            if (title in pendingCancels) {
                pendingCancels.remove(title)
                throw kotlinx.coroutines.CancellationException("sweep cancelled")
            }
            return covers[title]?.let {
                DiscoveryMeta(description = null, genres = emptyList(), altTitle = null, coverUrl = it)
            }
        }
    }

    @org.junit.After
    fun tearDown() = DiscoveryCoverRecovery.clear()

    @Test
    fun `targets are items with null or blank cover only`() {
        coverRecoveryTargets(listOf(sugg("A", null), sugg("B", ""), sugg("C", "http://c")))
            .map { it.title } shouldBe listOf("A", "B")
    }

    @Test
    fun `recovered urls are cached per media type and clean title`() {
        DiscoveryCoverRecovery.put(DiscoveryMediaType.NOVEL, "shadow slave", "http://r")
        DiscoveryCoverRecovery.get(DiscoveryMediaType.NOVEL, "shadow slave") shouldBe "http://r"
        DiscoveryCoverRecovery.get(DiscoveryMediaType.MANGA, "shadow slave") shouldBe null
    }

    @Test
    fun `sweep resolves missing covers through trending meta and counts recovered`() = runTest {
        val fake = FakeTrending(mapOf("Found" to "http://found-cover"))
        val recovered =
            recoverMissingCovers(listOf(sugg("Found", null), sugg("NoMeta", null)), DiscoveryMediaType.NOVEL, fake)
        recovered shouldBe 1
        DiscoveryCoverRecovery.get(DiscoveryMediaType.NOVEL, "found") shouldBe "http://found-cover"
    }

    @Test
    fun `null-meta titles are retried once then give up`() = runTest {
        val fake = FakeTrending(emptyMap())
        recoverMissingCovers(listOf(sugg("NoMeta", null)), DiscoveryMediaType.NOVEL, fake)
        fake.calls shouldBe 1 // первый sweep — попытка 1 из 2
        recoverMissingCovers(listOf(sugg("NoMeta", null)), DiscoveryMediaType.NOVEL, fake) shouldBe 0
        fake.calls shouldBe 2 // transient-сбой (оффлайн) прощается — второй sweep повторяет запрос
        recoverMissingCovers(listOf(sugg("NoMeta", null)), DiscoveryMediaType.NOVEL, fake) shouldBe 0
        fake.calls shouldBe 2 // MAX_RECOVERY_ATTEMPTS достигнут — шторма сети нет
    }

    @Test
    fun `cancelled fetch releases the attempt mark so next sweep retries`() = runTest {
        val fake = FakeTrending(mapOf("Found" to "http://found-cover"), cancelOn = setOf("Found"))
        val first = runCatching { recoverMissingCovers(listOf(sugg("Found", null)), DiscoveryMediaType.NOVEL, fake) }
        first.isFailure shouldBe true // CancellationException проброшен
        // отмена срабатывает ровно один раз — следующий sweep обязан повторить запрос и восстановить обложку
        recoverMissingCovers(listOf(sugg("Found", null)), DiscoveryMediaType.NOVEL, fake) shouldBe 1
        fake.calls shouldBe 2
        DiscoveryCoverRecovery.get(DiscoveryMediaType.NOVEL, "found") shouldBe "http://found-cover"
    }

    @Test
    fun `onRecovered fires once per recovered cover`() = runTest {
        val fake = FakeTrending(mapOf("A" to "http://a", "B" to "http://b"))
        var ticks = 0
        recoverMissingCovers(
            listOf(sugg("A", null), sugg("B", null)),
            DiscoveryMediaType.NOVEL,
            fake,
            onRecovered = { ticks++ },
        )
        ticks shouldBe 2
    }
}

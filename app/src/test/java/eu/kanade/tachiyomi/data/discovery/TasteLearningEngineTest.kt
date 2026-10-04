package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType

class TasteLearningEngineTest {

    private val now = 1_000_000_000_000L

    private fun signal(
        title: String,
        type: DiscoverySignalType,
        genres: List<String> = emptyList(),
        sourceKey: String? = null,
        createdAt: Long = now,
    ) = DiscoverySignal(
        mediaType = DiscoveryMediaType.ANIME,
        cleanTitle = title.lowercase(),
        title = title,
        signalType = type,
        genres = genres,
        provider = null,
        sourceKey = sourceKey,
        createdAt = createdAt,
    )

    // ==================== fold ====================

    @Test
    fun `positive signals accumulate genre weights`() {
        val profile = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.ADD, listOf("action", "comedy")),
                signal("b", DiscoverySignalType.LIKE, listOf("action")),
            ),
            nowMs = now,
        )
        val weights = profile.genres.toMap()
        (weights.getValue("action") > 1.5) shouldBe true
        (weights.getValue("comedy") > 0.5) shouldBe true
        profile.signalCount shouldBe 2
    }

    @Test
    fun `hide signal produces negative weight and is excluded from positive profile`() {
        val profile = foldLearnedTasteProfile(
            listOf(signal("x", DiscoverySignalType.HIDE, listOf("mecha"))),
            nowMs = now,
        )
        // Негативный жанр в позитивный профиль не попадает (лента не обязана его «любить»).
        profile.genres.any { it.first == "mecha" } shouldBe false
    }

    @Test
    fun `genre from k liked titles gets overlap multiplier`() {
        val single = foldLearnedTasteProfile(
            listOf(signal("a", DiscoverySignalType.LIKE, listOf("drama"))),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        val triple = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.LIKE, listOf("drama")),
                signal("b", DiscoverySignalType.LIKE, listOf("drama")),
                signal("c", DiscoverySignalType.LIKE, listOf("drama")),
            ),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        // k=3: буст (1 + 0.5·2) = 2x от суммы трёх — строго больше трёх одиночных? Нет:
        // 3 сигнала уже суммируются; проверяем что буст СВЕРХ суммы трёх «одиноких».
        (triple > 3 * single) shouldBe true
    }

    @Test
    fun `old signals decay against fresh ones`() {
        val old = foldLearnedTasteProfile(
            listOf(signal("old", DiscoverySignalType.ADD, listOf("drama"), createdAt = now - 90L * DAY_MS_TEST)),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        val fresh = foldLearnedTasteProfile(
            listOf(signal("new", DiscoverySignalType.ADD, listOf("drama"), createdAt = now)),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        (old < fresh) shouldBe true
    }

    @Test
    fun `vetoed genre is excluded from learned profile`() {
        val profile = foldLearnedTasteProfile(
            listOf(signal("a", DiscoverySignalType.LIKE, listOf("mecha", "drama"))),
            nowMs = now,
            vetoGenres = setOf("mecha"),
        )
        profile.genres.map { it.first } shouldBe listOf("drama")
    }

    @Test
    fun `genre weight is clamped`() {
        val many = (1..30).map { signal("t$it", DiscoverySignalType.ADD, listOf("isekai")) }
        val profile = foldLearnedTasteProfile(many, nowMs = now)
        profile.genres.toMap().getValue("isekai") shouldBe 3.0
    }

    @Test
    fun `source affinity sums signal weights per source`() {
        val profile = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.ADD, sourceKey = "pkg.x"),
                signal("b", DiscoverySignalType.LIKE, sourceKey = "pkg.x"),
                signal("c", DiscoverySignalType.HIDE, sourceKey = "pkg.y"),
            ),
            nowMs = now,
        )
        profile.sourceAffinity.getValue("pkg.x") shouldBe 1.8
        profile.sourceAffinity.getValue("pkg.y") shouldBe -1.0
    }

    // ==================== merge ====================

    @Test
    fun `merge keeps library profile when nothing learned`() {
        val library = listOf("action" to 2.0, "drama" to 1.0)
        mergeTasteProfiles(library, LearnedTasteProfile()) shouldBe library
    }

    @Test
    fun `merge takes max not sum for double-counted genre`() {
        val library = listOf("action" to 2.0)
        val learned = LearnedTasteProfile(
            genres = listOf("action" to 3.0),
            signalCount = 100, // blend = 1
        )
        val merged = mergeTasteProfiles(library, learned).toMap()
        merged.getValue("action") shouldBe 3.0
    }

    @Test
    fun `cold start blends linearly`() {
        val library = listOf("action" to 2.0)
        val learned = LearnedTasteProfile(
            genres = listOf("comedy" to 1.0),
            signalCount = 5, // blend = 5/20 = 0.25
        )
        val merged = mergeTasteProfiles(library, learned).toMap()
        merged.getValue("comedy") shouldBe 0.25
    }

    @Test
    fun `new learned genre joins library genres`() {
        val library = listOf("action" to 1.0)
        val learned = LearnedTasteProfile(
            genres = listOf("romance" to 2.0),
            signalCount = 40,
        )
        val merged = mergeTasteProfiles(library, learned)
        merged.map { it.first }.toSet() shouldBe setOf("action", "romance")
        merged.map { it.first }.first() shouldBe "romance"
    }

    // ==================== source affinity boost ====================

    @Test
    fun `affinity boost is monotonic and clamped`() {
        val zero = applySourceAffinity(libraryWeight = 10, affinity = 0.0)
        val positive = applySourceAffinity(libraryWeight = 10, affinity = 2.0)
        val huge = applySourceAffinity(libraryWeight = 10, affinity = 1000.0)
        val negative = applySourceAffinity(libraryWeight = 10, affinity = -1000.0)
        (positive > zero) shouldBe true
        (huge <= 20.0) shouldBe true
        (negative >= 4.0) shouldBe true
    }

    private companion object {
        const val DAY_MS_TEST = 24L * 60 * 60 * 1000
    }
}

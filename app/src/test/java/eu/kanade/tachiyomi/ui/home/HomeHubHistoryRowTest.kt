package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.Test

class HomeHubHistoryRowTest {

    private fun hero(id: Long = 1L) = HomeHubHero(
        entryId = id,
        title = "Last Read",
        progressNumber = 12.0,
        coverData = null,
    )

    private fun hist(id: Long) = HomeHubHistory(
        entryId = id,
        title = "H$id",
        progressNumber = 1.0,
        coverData = null,
        section = HomeHubSection.Novel,
    )

    @Test
    fun `collage and stage prepend hero first and keep the six card cap`() {
        val history = (2..7L).map { hist(it) }
        for (mode in listOf(HomeHeroMode.Collage, HomeHeroMode.Stage)) {
            val result = prependLastReadHero(hero(1L), history, mode, HomeHubSection.Novel)
            result.size shouldBe 6
            result.first().entryId shouldBe 1L
            result.first().title shouldBe "Last Read"
            // хвост сдвигается: 7-й вытеснен, порядок остальных сохранён
            result.last().entryId shouldBe 6L
        }
    }

    @Test
    fun `continue and hybrid leave history untouched - hero already spotlit`() {
        val history = (2..7L).map { hist(it) }
        for (mode in listOf(HomeHeroMode.Continue, HomeHeroMode.Hybrid)) {
            prependLastReadHero(hero(1L), history, mode, HomeHubSection.Novel) shouldBe history
        }
    }

    @Test
    fun `unresolved auto presentation does not prepend`() {
        // В прод-пути Auto резолвится в Stage/Continue раньше (resolveHeroPresentation);
        // защита от прямого передачи Auto — ряд не меняется.
        val history = listOf(hist(2))
        prependLastReadHero(hero(1L), history, HomeHeroMode.Auto, HomeHubSection.Novel) shouldBe history
    }

    @Test
    fun `null hero returns history unchanged`() {
        val history = listOf(hist(2))
        prependLastReadHero(null, history, HomeHeroMode.Stage, HomeHubSection.Novel) shouldBe history
    }

    @Test
    fun `hero already present in history is deduplicated`() {
        val history = listOf(hist(1), hist(2))
        val result = prependLastReadHero(hero(1L), history, HomeHeroMode.Collage, HomeHubSection.Novel)
        result.map { it.entryId } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `highlight id only for collage and stage with hero`() {
        lastReadHighlightId(hero(9L), HomeHeroMode.Stage) shouldBe 9L
        lastReadHighlightId(hero(9L), HomeHeroMode.Collage) shouldBe 9L
        lastReadHighlightId(hero(9L), HomeHeroMode.Continue) shouldBe null
        lastReadHighlightId(hero(9L), HomeHeroMode.Hybrid) shouldBe null
        lastReadHighlightId(hero(9L), HomeHeroMode.Auto) shouldBe null
        lastReadHighlightId(null, HomeHeroMode.Stage) shouldBe null
    }
}

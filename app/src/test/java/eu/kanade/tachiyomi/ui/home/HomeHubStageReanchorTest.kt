package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubStageReanchorTest {

    @Test
    fun `composition without items change keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = false,
            previousFocusedTitle = "solo leveling",
            newOrderedTitles = listOf("a", "solo leveling", "b"),
            currentCenter = 7,
        ) shouldBe 7
    }

    @Test
    fun `items change moves center to focused title new index`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "solo leveling",
            newOrderedTitles = listOf("a", "b", "c", "solo leveling", "d"),
            currentCenter = 9,
        ) shouldBe 3
    }

    @Test
    fun `items change with same index keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "b",
            newOrderedTitles = listOf("a", "b", "c"),
            currentCenter = 1,
        ) shouldBe 1
    }

    @Test
    fun `focused title removed by refresh resets scene to start`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "hidden title",
            newOrderedTitles = listOf("a", "b", "c"),
            currentCenter = 5,
        ) shouldBe 0
    }

    @Test
    fun `no previous focus keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = null,
            newOrderedTitles = listOf("a", "b"),
            currentCenter = 4,
        ) shouldBe 4
    }

    @Test
    fun `defensive empty new list keeps center`() {
        resolveStageReanchorCenter(
            itemsChanged = true,
            previousFocusedTitle = "a",
            newOrderedTitles = emptyList(),
            currentCenter = 2,
        ) shouldBe 2
    }
}

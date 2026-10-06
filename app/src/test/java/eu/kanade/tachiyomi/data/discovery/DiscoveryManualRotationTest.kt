package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoveryManualRotationTest {

    @Test
    fun `seed offset step is one per manual refresh`() {
        // Шаг 1: каждый следующий рефреш сдвигает окно сидов на одну позицию.
        (1..6).map(::manualSeedOffset) shouldContainExactly listOf(1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `seed rotation covers every window position for any pool size`() {
        // Шаг 1 взаимно прост с любым размером: size последовательных рефрешей
        // обходят ВСЕ позиции окна ( прежний шаг 2 + % 20 — только половину
        // при чётных размерах).
        for (size in 2..24) {
            val positions = (1..size).map { manualSeedOffset(it) % size }.toSet()
            positions.size shouldBe size
        }
    }

    @Test
    fun `page offset starts at two and cycles ten distinct pages`() {
        manualPageOffset(1) shouldBe 2
        val pages = (1..10).map(::manualPageOffset)
        pages.toSet().size shouldBe 10
        pages.min() shouldBe 2
        pages.max() shouldBe 11
    }

    @Test
    fun `page cycle repeats after ten refreshes`() {
        manualPageOffset(11) shouldBe 2
        manualPageOffset(12) shouldBe 3
        manualPageOffset(21) shouldBe 2
    }

    @Test
    fun `page offsets stay in provider-sane bounds`() {
        (1..50).map(::manualPageOffset).forEach { page ->
            (page in 2..11) shouldBe true
        }
    }
}

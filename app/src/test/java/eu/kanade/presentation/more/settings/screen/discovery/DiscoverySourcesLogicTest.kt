package eu.kanade.presentation.more.settings.screen.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoverySourcesLogicTest {

    private fun entry(key: String, name: String = "s$key", excluded: Boolean = false) =
        SourcePickUi(
            id = key.hashCode().toLong(),
            pluginKey = key,
            name = name,
            weight = 0,
            isLastUsed = false,
            isTop3Auto = false,
            excluded = excluded,
        )

    @Test
    fun `toggle excludes and includes`() {
        toggleExcluded(currentExcluded = setOf("p2"), key = "p1", allKeys = setOf("p1", "p2", "p3")) shouldBe
            setOf("p2", "p1")
        toggleExcluded(currentExcluded = setOf("p2"), key = "p2", allKeys = setOf("p1", "p2", "p3")) shouldBe
            emptySet()
    }

    @Test
    fun `toggle last allowed is guarded`() {
        toggleExcluded(currentExcluded = setOf("p2", "p3"), key = "p1", allKeys = setOf("p1", "p2", "p3")) shouldBe
            null
    }

    @Test
    fun `deselect all keeps weight top1`() {
        deselectAllKeepTop1(listOf("p11", "p22", "p33")) shouldBe setOf("p22", "p33")
        deselectAllKeepTop1(listOf("p11")) shouldBe emptySet()
        deselectAllKeepTop1(emptyList()) shouldBe emptySet()
    }

    @Test
    fun `query filter is case insensitive`() {
        val entries = listOf(entry("p1", "MangaDex"), entry("p2", "RanobeHub"))
        filterSourcePicks(entries, "manga").map { it.pluginKey } shouldBe listOf("p1")
        filterSourcePicks(entries, "  ").size shouldBe 2
    }

    @Test
    fun `overload warn above eight`() {
        isOverloadWarn(List(9) { entry("p$it") }) shouldBe true
        isOverloadWarn(List(8) { entry("p$it") }) shouldBe false
        isOverloadWarn(List(9) { entry("p$it", excluded = it < 2) }) shouldBe false
    }
}

package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoveryLikeSeedCandidatesTest {

    @Test
    fun `description original joins candidates ahead of track titles`() {
        val seed = DiscoverySeedInput(
            entryId = 1,
            title = "Магия в последний раз",
            description = "Original: Mahoutsukai no Yome\nОписание...",
            altTitles = listOf("The Last Magic Hour", "Track Alias"),
        )
        likeSeedCandidateTitles(seed) shouldBe
            listOf("Магия в последний раз", "Mahoutsukai no Yome", "The Last Magic Hour", "Track Alias")
    }

    @Test
    fun `normalized variant is emitted for suffixed titles`() {
        val seed = DiscoverySeedInput(entryId = 2, title = "Fate/Zero Season 2")
        val candidates = likeSeedCandidateTitles(seed)
        candidates shouldContain "Fate/Zero Season 2"
        candidates shouldContain "Fate/Zero"
    }

    @Test
    fun `blank and duplicate alt titles are filtered`() {
        val seed = DiscoverySeedInput(entryId = 3, title = "Solo Leveling", altTitles = listOf("  ", "X", "X"))
        likeSeedCandidateTitles(seed) shouldBe listOf("Solo Leveling", "X")
    }

    @Test
    fun `null description yields title plus alt titles only`() {
        val seed = DiscoverySeedInput(entryId = 4, title = "Solo Leveling", altTitles = listOf("Na Honjaman Level-Up"))
        likeSeedCandidateTitles(seed) shouldBe listOf("Solo Leveling", "Na Honjaman Level-Up")
    }
}

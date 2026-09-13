package eu.kanade.tachiyomi.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeHubProgressNumberFormatTest {

    @Test
    fun `fractional progress trims float widening noise`() {
        val widened = 3.2f.toDouble()

        assertEquals("3.2", formatProgressNumber(HomeHubSection.Novel, widened))
        assertEquals("3.2", formatProgressNumber(HomeHubSection.Manga, widened))
        assertEquals("3.2", formatProgressNumber(HomeHubSection.Anime, widened))
    }

    @Test
    fun `whole progress has no decimal suffix`() {
        assertEquals("351", formatProgressNumber(HomeHubSection.Novel, 351.0))
        assertEquals("351", formatProgressNumber(HomeHubSection.Anime, 351.0))
    }

    @Test
    fun `progress beyond three decimals rounds to app convention`() {
        assertEquals("10.123", formatProgressNumber(HomeHubSection.Manga, 10.1234))
    }
}

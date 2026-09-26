package eu.kanade.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemNumberFormatterTest {

    @Test
    fun `chapter number widened from float loses noise`() {
        assertEquals("3.2", formatChapterNumber(3.2f.toDouble()))
    }

    @Test
    fun `episode number widened from float loses noise`() {
        assertEquals("3.2", formatEpisodeNumber(3.2f.toDouble()))
    }

    @Test
    fun `whole numbers stay suffix-free`() {
        assertEquals("351", formatChapterNumber(351.0))
        assertEquals("351", formatEpisodeNumber(351.0))
    }
}

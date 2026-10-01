package eu.kanade.presentation.track

import eu.kanade.presentation.util.formatChapterNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackProgressTextTest {

    private val numbers = listOf(
        1.0,
        2.0,
        3.0,
        3.1f.toDouble(),
        3.2f.toDouble(),
        3.3f.toDouble(),
        4.0,
        5.0,
    )

    @Test
    fun `read ordinal counts position among fractional chapter numbers`() {
        assertEquals(5, resolveReadOrdinal(numbers, 3.2f.toDouble()))
        assertEquals(8, resolveReadOrdinal(numbers, 5.0))
        assertEquals(0, resolveReadOrdinal(numbers, 0.0))
    }

    @Test
    fun `read ordinal counts each chapter number once when rows duplicate it`() {
        // Regression: scanlator branches keep several rows per chapter number, and counting rows
        // inflated the tracking sheet numerator ("463 / 144") way past the real chapter count.
        val duplicated = listOf(1.0, 1.0, 2.0, 2.0, 2.0, 3.0, -1.0)
        assertEquals(3, resolveReadOrdinal(duplicated, 3.0))
        assertEquals(2, resolveReadOrdinal(duplicated, 2.0))
    }

    @Test
    fun `read ordinal is null without local chapters`() {
        assertNull(resolveReadOrdinal(emptyList(), 3.2f.toDouble()))
    }

    @Test
    fun `progress text prefers ordinal over chapter number in counts`() {
        assertEquals("5 / 8", trackProgressText(5, 3.2f.toDouble(), 8, ::formatChapterNumber))
        assertEquals("5", trackProgressText(5, 3.2f.toDouble(), 0, ::formatChapterNumber))
    }

    @Test
    fun `progress text falls back to chapter number when ordinal unknown`() {
        assertEquals("3.2 / 8", trackProgressText(null, 3.2f.toDouble(), 8, ::formatChapterNumber))
    }
}

package eu.kanade.presentation.reader.novel

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelPageReaderPageVerticalPaddingTest {

    @Test
    fun `navigation bar covered by rendered bottom inset is not reserved twice`() {
        // Portrait phone with gesture navigation: the rendered page already reserves
        // contentPadding (13px) + bookBottomInset (70px) below the text, which covers the
        // 39px gesture zone; the pagination budget must not subtract it a second time.
        val verticalPaddingPx = resolveNovelPageReaderVerticalPaddingPx(
            topPaddingPx = 85,
            bottomPaddingPx = 13,
            bookBottomInsetPx = 70,
            pageFitSafetyPx = 21,
            navigationBarHeightPx = 39,
        )

        assertEquals(85 + 13 + 70 + 21, verticalPaddingPx)
    }

    @Test
    fun `navigation bar taller than rendered bottom inset keeps only the uncovered part`() {
        // Small font (bookBottomInset at its 24dp floor) with 3-button navigation: the
        // rendered 13px + 39px do not cover the 78px bar, so the missing 26px stay reserved.
        val verticalPaddingPx = resolveNovelPageReaderVerticalPaddingPx(
            topPaddingPx = 85,
            bottomPaddingPx = 13,
            bookBottomInsetPx = 39,
            pageFitSafetyPx = 21,
            navigationBarHeightPx = 78,
        )

        assertEquals(85 + 13 + 39 + 21 + 26, verticalPaddingPx)
    }

    @Test
    fun `hidden navigation bar adds no bottom reservation`() {
        val verticalPaddingPx = resolveNovelPageReaderVerticalPaddingPx(
            topPaddingPx = 50,
            bottomPaddingPx = 13,
            bookBottomInsetPx = 70,
            pageFitSafetyPx = 21,
            navigationBarHeightPx = 0,
        )

        assertEquals(50 + 13 + 70 + 21, verticalPaddingPx)
    }
}

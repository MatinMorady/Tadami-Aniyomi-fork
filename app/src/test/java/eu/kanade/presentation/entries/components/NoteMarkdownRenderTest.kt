package eu.kanade.presentation.entries.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NoteMarkdownRenderTest {

    private val link = Color(0xFF0095FF)
    private val code = Color(0x1AFFFFFF)

    @Test
    fun `link span carries url link annotation`() {
        val text = buildNoteAnnotatedString(
            spans = listOf(NoteSpan.Text("Карта: "), NoteSpan.Link("карта", "https://example.org/map")),
            linkColor = link,
            codeBackground = code,
        )

        val links = text.getLinkAnnotations(0, text.length)

        links.size shouldBe 1
        (links.first().item as LinkAnnotation.Url).url shouldBe "https://example.org/map"
    }

    @Test
    fun `link annotation styles are underlined with accent colour`() {
        val text = buildNoteAnnotatedString(
            spans = listOf(NoteSpan.Link("карта", "https://example.org/map")),
            linkColor = link,
            codeBackground = code,
        )

        val styles = (text.getLinkAnnotations(0, text.length).first().item as LinkAnnotation.Url).styles

        styles?.style?.textDecoration shouldBe TextDecoration.Underline
        styles?.style?.color shouldBe link
    }

    @Test
    fun `code span uses monospace`() {
        val text = buildNoteAnnotatedString(
            spans = listOf(NoteSpan.Text("ключ ", code = false), NoteSpan.Text("NORTH-7", code = true)),
            linkColor = link,
            codeBackground = code,
        )

        text.spanStyles.any { it.item.fontFamily != null } shouldBe true
    }

    @Test
    fun `all blocks fit when heights are below the limit`() {
        visibleBlockCount(listOf(18, 18, 18), limitPx = 90) shouldBe 3
    }

    @Test
    fun `the first block that does not fit is hidden`() {
        visibleBlockCount(listOf(18, 18, 36), limitPx = 54) shouldBe 2
    }

    @Test
    fun `block ending exactly at the limit stays visible`() {
        visibleBlockCount(listOf(30, 24), limitPx = 54) shouldBe 2
    }

    @Test
    fun `non empty input always shows at least one block`() {
        visibleBlockCount(listOf(400, 100), limitPx = 90) shouldBe 1
    }

    @Test
    fun `empty input shows nothing`() {
        visibleBlockCount(emptyList(), limitPx = 90) shouldBe 0
    }

    @Test
    fun `oversized paragraph is clamped to the remaining budget`() {
        paragraphClampLines(blockHeightPx = 400, remainingPx = 90, lineHeightPx = 18) shouldBe 5
    }

    @Test
    fun `paragraph that fits fully is not clamped`() {
        paragraphClampLines(blockHeightPx = 54, remainingPx = 90, lineHeightPx = 18) shouldBe null
    }

    @Test
    fun `toggle appears when the last visible paragraph is clamped`() {
        showNoteToggle(visibleCount = 1, totalCount = 1, paragraphClamped = true) shouldBe true
    }

    @Test
    fun `expanded collapse is needed for a single block above the limit`() {
        expandedCollapseNeeded(listOf(400), limitPx = 90) shouldBe true
    }

    @Test
    fun `expanded collapse is needed when a block is hidden`() {
        expandedCollapseNeeded(listOf(18, 18, 18), limitPx = 40) shouldBe true
    }

    @Test
    fun `expanded collapse is not needed when everything fits`() {
        expandedCollapseNeeded(listOf(18, 18, 18), limitPx = 90) shouldBe false
    }

    @Test
    fun `expanded collapse is not needed for an empty note`() {
        expandedCollapseNeeded(emptyList(), limitPx = 90) shouldBe false
    }

    @Test
    fun `no toggle when everything fits`() {
        showNoteToggle(visibleCount = 3, totalCount = 3, paragraphClamped = false) shouldBe false
    }
}

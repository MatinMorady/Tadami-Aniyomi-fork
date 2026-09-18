package eu.kanade.presentation.entries.components

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class NoteMarkdownTest {

    private fun plainText(blocks: List<NoteBlock>): String = blocks.joinToString("\n") { block ->
        val spans = when (block) {
            is NoteBlock.Paragraph -> block.spans
            is NoteBlock.Bullet -> block.spans
            is NoteBlock.Ordered -> block.spans
            is NoteBlock.Quote -> block.spans
            NoteBlock.Divider -> emptyList()
        }
        spans.joinToString("") { span ->
            when (span) {
                is NoteSpan.Text -> span.text
                is NoteSpan.Link -> span.text
            }
        }
    }

    @Test
    fun `plain note keeps its text unchanged`() {
        val note = "Читать с 12 главы, дальше по интерлюдиям.\nВторой абзац без пустой строки."

        val blocks = NoteMarkdown.parse(note)

        blocks.size shouldBe 1
        plainText(blocks) shouldBe note
    }

    @Test
    fun `blank line separates paragraphs`() {
        val blocks = NoteMarkdown.parse("Первый абзац.\n\nВторой абзац.")

        blocks.size shouldBe 2
        blocks.forEach { it.shouldBeInstanceOf<NoteBlock.Paragraph>() }
        plainText(blocks) shouldBe "Первый абзац.\nВторой абзац."
    }

    @Test
    fun `bullet list keeps items and nesting depth`() {
        val blocks = NoteMarkdown.parse("- Пролог\n- Арка 2\n  - главы 50-52")

        blocks.size shouldBe 3
        blocks[0].shouldBeInstanceOf<NoteBlock.Bullet>()
        (blocks[0] as NoteBlock.Bullet).depth shouldBe 0
        (blocks[2] as NoteBlock.Bullet).depth shouldBe 1
        plainText(blocks) shouldBe "Пролог\nАрка 2\nглавы 50-52"
    }

    @Test
    fun `lazy ordered list is renumbered`() {
        val blocks = NoteMarkdown.parse("1. раз\n1. два\n1. три")

        blocks.filterIsInstance<NoteBlock.Ordered>().map { it.number } shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `explicit ordered numbers are preserved`() {
        val blocks = NoteMarkdown.parse("3. раз\n7. два")

        blocks.filterIsInstance<NoteBlock.Ordered>().map { it.number } shouldBe listOf(3, 7)
    }

    @Test
    fun `inline styles are parsed`() {
        val spans = (NoteMarkdown.parse("**жирный** и `код`")[0] as NoteBlock.Paragraph).spans
        val bold = spans.filterIsInstance<NoteSpan.Text>().single { it.bold }
        val code = spans.filterIsInstance<NoteSpan.Text>().single { it.code }

        bold.text shouldBe "жирный"
        code.text shouldBe "код"
    }

    @Test
    fun `atx heading becomes bold paragraph`() {
        val blocks = NoteMarkdown.parse("## Арка «Кровавая луна»")

        blocks.size shouldBe 1
        val spans = (blocks[0] as NoteBlock.Paragraph).spans
        spans.single().shouldBeInstanceOf<NoteSpan.Text>()
        val text = spans.single() as NoteSpan.Text
        text.text shouldBe "Арка «Кровавая луна»"
        text.bold shouldBe true
    }

    @Test
    fun `markdown link and bare url both become links`() {
        val spans = (
            NoteMarkdown.parse("[карта](https://example.org/map) и https://example.org/x")[0]
                as NoteBlock.Paragraph
            ).spans
        val links = spans.filterIsInstance<NoteSpan.Link>()

        links.size shouldBe 2
        links[0] shouldBe NoteSpan.Link("карта", "https://example.org/map")
        links[1].url shouldBe "https://example.org/x"
    }

    @Test
    fun `angle bracket autolink becomes link`() {
        val spans = (NoteMarkdown.parse("см. <https://example.org/a>")[0] as NoteBlock.Paragraph).spans
        val link = spans.filterIsInstance<NoteSpan.Link>().single()

        link.url shouldBe "https://example.org/a"
        link.text shouldBe "https://example.org/a"
    }

    @Test
    fun `structured note keeps all text content`() {
        val note = "- Пролог с **жирным** и https://example.org/x\n- Арка 2"

        plainText(NoteMarkdown.parse(note)) shouldBe
            "Пролог с жирным и https://example.org/x\nАрка 2"
    }

    @Test
    fun `crlf soft break is normalized to lf`() {
        plainText(NoteMarkdown.parse("Первый\r\nВторой")) shouldBe "Первый\nВторой"
    }

    @Test
    fun `unclosed markers stay literal`() {
        val note = "**не закрыт и `тоже"

        plainText(NoteMarkdown.parse(note)) shouldBe note
    }

    @Test
    fun `horizontal rule becomes divider`() {
        NoteMarkdown.parse("---") shouldBe listOf(NoteBlock.Divider)
    }

    @Test
    fun `blank note yields no blocks`() {
        NoteMarkdown.parse("   \n\n ") shouldBe emptyList()
    }

    @Test
    fun `url before a closing parenthesis keeps both characters`() {
        val note = "см. (https://example.org/x)."

        plainText(NoteMarkdown.parse(note)) shouldBe note
    }

    @Test
    fun `url with balanced parentheses stays a single link`() {
        val note = "https://en.wikipedia.org/wiki/Foo_(bar)"

        val link = NoteMarkdown.parse(note)
            .filterIsInstance<NoteBlock.Paragraph>()
            .flatMap { it.spans }
            .filterIsInstance<NoteSpan.Link>()
            .single()

        link.url shouldBe note
        plainText(NoteMarkdown.parse(note)) shouldBe note
    }

    @Test
    fun `snake case stays literal`() {
        val note = "foo_bar и baz_qux"

        val spans = NoteMarkdown.parse(note).filterIsInstance<NoteBlock.Paragraph>().flatMap { it.spans }

        spans.filterIsInstance<NoteSpan.Text>().none { it.italic } shouldBe true
        plainText(NoteMarkdown.parse(note)) shouldBe note
    }

    @Test
    fun `explicit numbers inside a list body are kept as markers`() {
        val blocks = NoteMarkdown.parse("1. первое\n2023. год")

        blocks.filterIsInstance<NoteBlock.Ordered>().map { it.number } shouldBe listOf(1, 2023)
        plainText(blocks) shouldBe "первое\nгод"
    }

    @Test
    fun `bulleted item with a continuation line keeps its text`() {
        val note = "- a\n  продолжение"

        plainText(NoteMarkdown.parse(note)) shouldBe "a\n  продолжение"
    }

    @Test
    fun `double backtick code span drops the extra ticks`() {
        val spans = NoteMarkdown.parse("``x``").filterIsInstance<NoteBlock.Paragraph>().flatMap { it.spans }

        val code = spans.filterIsInstance<NoteSpan.Text>().single { it.code }
        code.text shouldBe "x"
        spans.filterIsInstance<NoteSpan.Text>().sumOf { it.text.length } shouldBe 1
    }

    @Test
    fun `fifty kilobyte note parses`() {
        val note = ("- пункт с **разметкой** и https://example.org/x\n").repeat(1200)

        NoteMarkdown.parse(note).size shouldBe 1200
    }
}

package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/** Строк заметки в свёрнутой карточке — как `collapsedLines` в карточке описания. */
private const val COLLAPSED_NOTE_LINES = 5

private val bulletMarkers = listOf("•", "◦", "▸")
private val noteTextStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp)

internal fun buildNoteAnnotatedString(
    spans: List<NoteSpan>,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is NoteSpan.Text -> {
                val style = span.spanStyle(codeBackground)
                if (span.hasStyle) withStyle(style) { append(span.text) } else append(span.text)
            }

            is NoteSpan.Link -> withLink(
                LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(span.text)
            }
        }
    }
}

/** Сколько первых блоков влезает в [limitPx]; 0 — если блоков нет; минимум 1 — если блоки есть. */
internal fun visibleBlockCount(blockHeightsPx: List<Int>, limitPx: Int): Int {
    var used = 0
    var count = 0
    for (height in blockHeightsPx) {
        if (used + height > limitPx) break
        used += height
        count++
    }
    return if (count == 0 && blockHeightsPx.isNotEmpty()) 1 else count
}

/** Сколько строк показать у последнего видимого абзаца: null — блок влезает целиком; иначе ≥ 1. */
internal fun paragraphClampLines(blockHeightPx: Int, remainingPx: Int, lineHeightPx: Int): Int? {
    if (lineHeightPx <= 0 || blockHeightPx <= remainingPx) return null
    return maxOf(1, remainingPx / lineHeightPx)
}

/** «Показать ещё»/«Свернуть» нужны, если скрыт хотя бы один блок или последний видимый абзац обрезан. */
internal fun showNoteToggle(visibleCount: Int, totalCount: Int, paragraphClamped: Boolean): Boolean =
    paragraphClamped || visibleCount < totalCount

/**
 * Нужен ли «Свернуть» в развёрнутом виде — то есть потеряет ли что-то свёрнутый вид.
 * Отдельная проверка на блок выше лимита обязательна: [visibleBlockCount] ради инварианта
 * «карточка не пустая» возвращает минимум 1 и сам по себе не отличает «влезло» от «обрезано».
 */
internal fun expandedCollapseNeeded(blockHeightsPx: List<Int>, limitPx: Int): Boolean =
    blockHeightsPx.sum() > limitPx ||
        visibleBlockCount(blockHeightsPx, limitPx) < blockHeightsPx.size

/**
 * Renders a note as markdown blocks. The collapsed height is bounded by [COLLAPSED_NOTE_LINES] lines
 * of [noteTextStyle]: blocks are composed and measured one by one until the budget is spent, so a long
 * note costs only the blocks that fit. The one paragraph that overflows is clamped by lines —
 * otherwise a note without any markdown would grow the card without bound.
 */
@Composable
fun NoteBlocks(
    note: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(note) { NoteMarkdown.parse(note) }
    val colors = AuroraTheme.colors
    val lineHeightPx = with(LocalDensity.current) { noteTextStyle.lineHeight.toPx() }
    val limitPx = (lineHeightPx * COLLAPSED_NOTE_LINES).roundToInt()

    SubcomposeLayout(modifier = modifier) { constraints ->
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placed = mutableListOf<Placeable>()
        var used = 0
        var clamped = false
        var collapsible = false

        if (expanded) {
            val heights = mutableListOf<Int>()
            blocks.forEachIndexed { index, block ->
                val placeable = subcompose("block-$index") { NoteBlockContent(block, colors) }
                    .first()
                    .measure(childConstraints)
                placed += placeable
                used += placeable.height
                heights += placeable.height
            }
            collapsible = expandedCollapseNeeded(heights, limitPx)
        } else {
            var index = 0
            while (index < blocks.size) {
                val block = blocks[index]
                val placeable = subcompose("block-$index") { NoteBlockContent(block, colors) }
                    .first()
                    .measure(childConstraints)

                if (used + placeable.height <= limitPx) {
                    placed += placeable
                    used += placeable.height
                    index++
                    continue
                }

                val lines = (block as? NoteBlock.Paragraph)
                    ?.let { paragraphClampLines(placeable.height, limitPx - used, lineHeightPx.roundToInt()) }
                if (lines != null) {
                    val clampedPlaceable = subcompose("clamp-$index") {
                        NoteBlockContent(block, colors, maxLines = lines)
                    }.first().measure(childConstraints)
                    placed += clampedPlaceable
                    used += clampedPlaceable.height
                    clamped = true
                } else if (placed.isEmpty()) {
                    placed += placeable
                    used += placeable.height
                }
                break
            }
        }

        val toggleNeeded = if (expanded) collapsible else showNoteToggle(placed.size, blocks.size, clamped)
        val toggle = if (toggleNeeded) {
            subcompose("toggle") {
                Text(
                    text = stringResource(
                        if (expanded) MR.strings.notes_collapse else MR.strings.notes_show_more,
                    ),
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded,
                        ),
                )
            }.first().measure(childConstraints)
        } else {
            null
        }

        val totalHeight = used + (toggle?.height ?: 0)
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            placed.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height
            }
            toggle?.place(0, y)
        }
    }
}

@Composable
private fun NoteBlockContent(
    block: NoteBlock,
    colors: AuroraColors,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = colors.accent
    val codeBackground = colors.textPrimary.copy(alpha = 0.08f)

    when (block) {
        is NoteBlock.Paragraph -> NoteText(
            spans = block.spans,
            linkColor = linkColor,
            codeBackground = codeBackground,
            color = colors.textPrimary,
            maxLines = maxLines,
        )

        is NoteBlock.Quote -> NoteText(
            spans = block.spans,
            linkColor = linkColor,
            codeBackground = codeBackground,
            color = colors.textSecondary,
            fontStyle = FontStyle.Italic,
        )

        is NoteBlock.Bullet -> NoteListItem(
            marker = bulletMarkers[block.depth % bulletMarkers.size],
            spans = block.spans,
            depth = block.depth,
            colors = colors,
            linkColor = linkColor,
            codeBackground = codeBackground,
        )

        is NoteBlock.Ordered -> NoteListItem(
            marker = "${block.number}.",
            spans = block.spans,
            depth = block.depth,
            colors = colors,
            linkColor = linkColor,
            codeBackground = codeBackground,
        )

        NoteBlock.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(1.dp)
                .background(colors.divider),
        )
    }
}

@Composable
private fun NoteText(
    spans: List<NoteSpan>,
    linkColor: Color,
    codeBackground: Color,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    fontStyle: FontStyle? = null,
) {
    Text(
        text = buildNoteAnnotatedString(spans = spans, linkColor = linkColor, codeBackground = codeBackground),
        style = noteTextStyle,
        color = color,
        fontStyle = fontStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Маркер в отдельной ячейке — перенос строки выравнивается по тексту, а не по маркеру. */
@Composable
private fun NoteListItem(
    marker: String,
    spans: List<NoteSpan>,
    depth: Int,
    colors: AuroraColors,
    linkColor: Color,
    codeBackground: Color,
) {
    Row(
        modifier = Modifier.padding(start = (depth * 14).dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.width(12.dp)) {
            Text(
                text = marker,
                style = noteTextStyle,
                color = colors.accent,
            )
        }
        Text(
            text = buildNoteAnnotatedString(
                spans = spans,
                linkColor = linkColor,
                codeBackground = codeBackground,
            ),
            style = noteTextStyle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private val NoteSpan.Text.hasStyle: Boolean
    get() = bold || italic || code || strike

private fun NoteSpan.Text.spanStyle(codeBackground: Color): SpanStyle {
    var style = SpanStyle()
    if (bold) style = style.copy(fontWeight = FontWeight.SemiBold)
    if (italic) style = style.copy(fontStyle = FontStyle.Italic)
    if (strike) style = style.copy(textDecoration = TextDecoration.LineThrough)
    if (code) style = style.copy(fontFamily = FontFamily.Monospace, background = codeBackground)
    return style
}

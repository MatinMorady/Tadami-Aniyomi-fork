package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal enum class NovelTextColorSwatchKind { AUTO, PRESET, CUSTOM }

internal data class NovelTextColorSwatchSelection(
    val kind: NovelTextColorSwatchKind,
    val presetIndex: Int,
)

/**
 * Maps the saved [globalTextColor] value onto a swatch: blank = Auto, a value equal to a preset
 * (compared as parsed ARGB, so `#AARRGGBB` picker output matches `#RRGGBB` presets) = that preset,
 * anything else = the custom swatch.
 */
internal fun resolveTextColorSwatchSelection(
    savedValue: String,
    presetHexes: List<String>,
): NovelTextColorSwatchSelection {
    if (savedValue.isBlank()) {
        return NovelTextColorSwatchSelection(NovelTextColorSwatchKind.AUTO, -1)
    }
    val saved = parseReaderColor(savedValue)
        ?: return NovelTextColorSwatchSelection(NovelTextColorSwatchKind.CUSTOM, -1)
    val presetIndex = presetHexes.indexOfFirst { parseReaderColor(it) == saved }
    return if (presetIndex >= 0) {
        NovelTextColorSwatchSelection(NovelTextColorSwatchKind.PRESET, presetIndex)
    } else {
        NovelTextColorSwatchSelection(NovelTextColorSwatchKind.CUSTOM, -1)
    }
}

private val novelTextColorAutoBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to Color(0xFFEDEDED),
        0.5f to Color(0xFFEDEDED),
        0.5f to Color(0xFF171717),
        1f to Color(0xFF171717),
    ),
)

private val novelTextColorCustomBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFC94D),
        Color(0xFF6BD47F),
        Color(0xFF5BC8F5),
        Color(0xFFB57BEE),
        Color(0xFFFF6B6B),
    ),
)

@Composable
internal fun NovelTextColorSwatchRow(
    savedValue: String,
    onSelectHex: (String) -> Unit,
) {
    val selection = remember(savedValue) {
        resolveTextColorSwatchSelection(savedValue, novelReaderTextColorPresetHexes)
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = NovelGlassContentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            NovelTextColorSwatch(
                caption = stringResource(AYMR.strings.novel_reader_text_swatch_auto),
                selected = selection.kind == NovelTextColorSwatchKind.AUTO,
                brush = novelTextColorAutoBrush,
                onClick = { onSelectHex("") },
            )
        }
        itemsIndexed(novelReaderTextColorPresetHexes) { index, hex ->
            val fill = remember(hex) { parseReaderColor(hex) }
            NovelTextColorSwatch(
                caption = novelTextColorPresetCaption(hex),
                selected = selection.kind == NovelTextColorSwatchKind.PRESET && selection.presetIndex == index,
                fill = fill,
                onClick = { onSelectHex(hex) },
            )
        }
        item {
            val customFill = if (selection.kind == NovelTextColorSwatchKind.CUSTOM) {
                remember(savedValue) { parseReaderColor(savedValue) }
            } else {
                null
            }
            NovelTextColorSwatch(
                caption = stringResource(
                    if (customFill != null) {
                        AYMR.strings.novel_reader_text_swatch_custom_set
                    } else {
                        AYMR.strings.novel_reader_text_swatch_custom
                    },
                ),
                selected = customFill != null,
                fill = customFill,
                brush = if (customFill == null) novelTextColorCustomBrush else null,
                onClick = { showPicker = true },
            )
        }
    }
    if (showPicker) {
        val initialArgb = remember(savedValue) {
            parseReaderColor(savedValue)?.toArgb() ?: -1
        }
        NovelTextColorPickerSheet(
            initial = initialArgb.toLong() and 0xFFFFFFFFL,
            onPick = { argb -> onSelectHex(formatNovelHighlightHexColor(argb)) },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun NovelTextColorSwatch(
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    fill: Color? = null,
    brush: Brush? = null,
) {
    val colors = AuroraTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.accent else auroraRimColor(),
                    shape = CircleShape,
                )
                .padding(3.dp)
                .then(
                    when {
                        brush != null -> Modifier.background(brush, CircleShape)
                        else -> Modifier.background(fill ?: colors.surface, CircleShape)
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (fill != null) {
                Text(
                    text = "A",
                    color = if (fill.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF2F2F2),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun novelTextColorPresetCaption(hex: String): String {
    return when (hex) {
        "#FFFFFF" -> stringResource(AYMR.strings.novel_reader_text_swatch_white)
        "#F1E4C8" -> stringResource(AYMR.strings.novel_reader_text_swatch_cream)
        "#FFB74D" -> stringResource(AYMR.strings.novel_reader_text_swatch_amber)
        "#90CAF9" -> stringResource(AYMR.strings.novel_reader_text_swatch_sky)
        "#AAAAAA" -> stringResource(AYMR.strings.novel_reader_text_swatch_gray)
        "#111111" -> stringResource(AYMR.strings.novel_reader_text_swatch_black)
        else -> hex
    }
}

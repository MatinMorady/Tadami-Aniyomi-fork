package eu.kanade.presentation.reader.novel

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Glass (blur-behind) arbitrary-color picker for the global reader text color.
 * Same recipe as [NovelImageActionsDialog]: [AdaptiveSheet] over a system-blurred, dimmed window.
 * Reports the picked ARGB long through [onPick] and closes itself; callers format it with
 * [formatNovelHighlightHexColor].
 */
@Composable
internal fun NovelTextColorPickerSheet(
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    val supportsBlurBehind = rememberSupportsBlurBehind(aurora.isEInk)

    val sheetContainer = remember(aurora.isDark, aurora.isEInk, supportsBlurBehind) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            !supportsBlurBehind -> aurora.surface
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora, sheetContainer) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    var red by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var green by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var blue by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }
    var hexInput by remember { mutableStateOf(formatNovelHighlightHexColor(initial)) }

    fun currentArgb(): Long {
        val r = red.toInt().coerceIn(0, 255)
        val g = green.toInt().coerceIn(0, 255)
        val b = blue.toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun applyArgb(argb: Long) {
        red = ((argb shr 16) and 0xFF).toFloat()
        green = ((argb shr 8) and 0xFF).toFloat()
        blue = (argb and 0xFF).toFloat()
        hexInput = formatNovelHighlightHexColor(argb)
    }

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.border(
                width = 1.dp,
                color = auroraRimColor(),
                shape = sheetShape,
            ),
            containerColor = sheetContainer,
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        val glass = ((step / 20f - 0.18f) / 0.82f).coerceIn(0f, 1f)
                        val radius = if (glass <= 0.02f) 0 else (44f * glass).roundToInt().coerceIn(1, 48)
                        val attrs = w.attributes
                        if (attrs.blurBehindRadius != radius) {
                            w.attributes = attrs.apply { blurBehindRadius = radius }
                        }
                        w.setDimAmount(0.18f * glass)
                    }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pageMaxHeight),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (aurora.isDark) {
                                    Color.White.copy(alpha = 0.24f)
                                } else {
                                    Color.Black.copy(alpha = 0.16f)
                                },
                            ),
                    )
                }

                Text(
                    text = stringResource(AYMR.strings.novel_reader_text_color_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = aurora.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NOVEL_HIGHLIGHT_PRESET_COLORS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(preset))
                                .border(width = 1.dp, color = auroraRimColor(), shape = CircleShape)
                                .clickable { applyArgb(preset) },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(currentArgb()))
                            .border(width = 2.dp, color = aurora.accent, shape = CircleShape),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    NovelTextColorSliderRow(label = "R", value = red, onChange = {
                        red = it
                        hexInput = formatNovelHighlightHexColor(currentArgb())
                    })
                    NovelTextColorSliderRow(label = "G", value = green, onChange = {
                        green = it
                        hexInput = formatNovelHighlightHexColor(currentArgb())
                    })
                    NovelTextColorSliderRow(label = "B", value = blue, onChange = {
                        blue = it
                        hexInput = formatNovelHighlightHexColor(currentArgb())
                    })
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            hexInput = input
                            parseNovelHighlightHexColor(input)?.let { argb -> applyArgb(argb) }
                        },
                        singleLine = true,
                        label = { Text(text = "#RRGGBB") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(MR.strings.action_cancel),
                            color = aurora.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    TextButton(onClick = {
                        onPick(currentArgb())
                        onDismiss()
                    }) {
                        Text(
                            text = stringResource(MR.strings.action_ok),
                            color = aurora.accent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NovelTextColorSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = AuroraTheme.colors.textSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
    }
}

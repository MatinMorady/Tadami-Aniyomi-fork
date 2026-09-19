package eu.kanade.presentation.util

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import java.util.function.Consumer
import android.graphics.Color as AndroidColor

/**
 * Returns whether real-time cross-window blur is supported and enabled on the current device.
 *
 * Checks:
 * 1. Android version >= 12 (API 31, Build.VERSION_CODES.S)
 * 2. Device is not in E-Ink mode
 * 3. System [WindowManager.isCrossWindowBlurEnabled] is true (accounts for OEM disablement,
 *    e.g. MIUI/HyperOS on budget devices like Redmi 10C, battery saver, or accessibility settings).
 */
@Composable
fun rememberSupportsBlurBehind(isEInk: Boolean = AuroraTheme.colors.isEInk): Boolean {
    if (isEInk || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    } ?: return false

    var isBlurEnabled by remember(windowManager) {
        mutableStateOf(
            try {
                windowManager.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
                false
            },
        )
    }

    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled ->
            isBlurEnabled = enabled
        }
        try {
            windowManager.addCrossWindowBlurEnabledListener(listener)
        } catch (_: Throwable) {
            // Some OEM ROMs might fail to register listeners
        }
        onDispose {
            try {
                windowManager.removeCrossWindowBlurEnabledListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    return resolveSupportsBlurBehind(
        sdkInt = Build.VERSION.SDK_INT,
        isEInk = isEInk,
        isCrossWindowBlurEnabled = isBlurEnabled,
    )
}

/**
 * Pure policy function for resolving whether window blur should be active.
 */
internal fun resolveSupportsBlurBehind(
    sdkInt: Int = Build.VERSION.SDK_INT,
    isEInk: Boolean = false,
    isCrossWindowBlurEnabled: Boolean = false,
): Boolean {
    if (isEInk || sdkInt < Build.VERSION_CODES.S) return false
    return isCrossWindowBlurEnabled
}

/**
 * Aurora glass chrome for centered dialogs: transparent window, cross-window blur behind
 * (Android 12+), translucent panel (70% black in dark / 88% white in light) with a thin rim.
 * Where blur is unavailable (older Android, E-Ink) the panel falls back to an opaque surface
 * and the classic dim scrim, so text never floats over a see-through background.
 *
 * Same language as NovelBookBuildDialog / ReaderPageActionsDialog / NovelImageActionsDialog,
 * extracted so dialogs do not re-copy the window-chrome boilerplate. Content owns its padding;
 * the column is height-bounded (680.dp), so children may use weight for scrollable regions.
 */
@Composable
fun AuroraBlurBehindDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val supportsBlurBehind = rememberSupportsBlurBehind(colors.isEInk)
    val shape = RoundedCornerShape(AURORA_DIALOG_CORNER_DP.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window

        // One-shot window chrome setup - never add/clear BLUR flags per frame (flicker source).
        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null) {
                w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                w.setDimAmount(0f)
                if (supportsBlurBehind) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
            onDispose {
                if (w != null && supportsBlurBehind) {
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                    w.setDimAmount(0f)
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        // Settled glass: a centered dialog has no drag reveal to animate from.
        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            if (supportsBlurBehind) {
                w.attributes = w.attributes.apply { blurBehindRadius = AURORA_DIALOG_BLUR_RADIUS }
                w.setDimAmount(AURORA_DIALOG_BLUR_DIM)
            } else {
                w.setDimAmount(AURORA_DIALOG_FALLBACK_DIM)
            }
        }

        Surface(
            shape = shape,
            color = resolveAuroraBlurBehindDialogContainerColor(colors, supportsBlurBehind),
            tonalElevation = 0.dp,
            // Transparent window clips elevation shadows, so the card is rimmed instead.
            shadowElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth(fraction = 0.92f)
                .widthIn(max = 480.dp)
                .heightIn(max = 680.dp)
                .border(width = 1.dp, color = auroraRimColor(), shape = shape),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
internal fun resolveAuroraBlurBehindDialogContainerColor(
    colors: AuroraColors,
    supportsBlurBehind: Boolean,
): Color = when {
    colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
    !supportsBlurBehind -> colors.surface
    colors.isDark -> Color.Black.copy(alpha = AURORA_DIALOG_DARK_PANEL_ALPHA)
    else -> Color.White.copy(alpha = AURORA_DIALOG_LIGHT_PANEL_ALPHA)
}

private const val AURORA_DIALOG_CORNER_DP = 28
private const val AURORA_DIALOG_BLUR_RADIUS = 44
private const val AURORA_DIALOG_BLUR_DIM = 0.18f
private const val AURORA_DIALOG_FALLBACK_DIM = 0.26f
private const val AURORA_DIALOG_DARK_PANEL_ALPHA = 0.70f
private const val AURORA_DIALOG_LIGHT_PANEL_ALPHA = 0.88f

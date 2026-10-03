package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

/**
 * Shared read/unread grammar for Aurora entry lists (novel / manga / anime):
 * the leading slot shows unread-presence, a read checkmark or a selection checkbox.
 * Read entries dim their text only — state markers stay fully opaque.
 */
const val AURORA_ENTRY_READ_DIM_ALPHA = 0.55f

@Composable
fun AuroraEntryStateSlot(
    selectionMode: Boolean,
    selected: Boolean,
    read: Boolean,
    modifier: Modifier = Modifier,
    unreadContent: @Composable () -> Unit,
    readContent: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            selectionMode -> AuroraSelectionCheckbox(selected = selected)
            read -> readContent()
            else -> unreadContent()
        }
    }
}

@Composable
fun AuroraSelectionCheckbox(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(colors.accent, CircleShape)
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = colors.textSecondary.copy(alpha = 0.55f),
                        shape = CircleShape,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Done,
                contentDescription = null,
                tint = colors.textOnAccent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun AuroraUnreadDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AuroraTheme.colors.accent),
    )
}

@Composable
fun AuroraReadDoneMark(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.Done,
        contentDescription = null,
        tint = AuroraTheme.colors.accent,
        modifier = modifier.size(18.dp),
    )
}

@Composable
fun PassiveDownloadStatus(
    downloaded: Boolean,
    downloading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            downloading -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = colors.accent,
            )
            downloaded -> Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp),
            )
            else -> Unit
        }
    }
}

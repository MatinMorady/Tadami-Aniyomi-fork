package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Session-scoped last reels source (device report: re-entering the feed opened a different
 * source than the one the user picked). The disk preference (lastUsedReelsSource) is
 * deliberately NOT written while the source's incognito state is active (global incognito,
 * the NSFW policy or the per-extension incognito set — see GetAnimeIncognitoState), so under
 * those policies the disk fallback degraded to "the first installed feed source".
 *
 * This in-memory holder still remembers the choice for the living process: leaving and
 * re-entering the feed returns to the same source without leaving a disk trace. A process
 * restart resets it to the disk preference (or the first source), which is exactly what the
 * incognito policies promise.
 *
 * Backed by Compose snapshot state so entry points (MoreTab) recompute the target source the
 * moment the selection changes, even when no disk write happens.
 */
object ReelsSessionSource {

    var lastSourceId by mutableStateOf<Long?>(null)
}

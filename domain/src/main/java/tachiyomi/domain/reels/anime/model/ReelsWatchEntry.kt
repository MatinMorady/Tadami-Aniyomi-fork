package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * One watched reel (host-side history, contract-independent): the last-watched position
 * powers the "Recent" surface and the resume-seek when the clip is rewatched. One row per
 * (videoId, sourceId) — the table stays tiny by design.
 */
data class ReelsWatchEntry(
    val videoId: String,
    val sourceId: Long,
    val title: String?,
    val author: String?,
    val posterUrl: String?,
    val webUrl: String?,
    val videoUrl: String,
    val durationSec: Double?,
    val positionMs: Long,
    val watchedAt: Date,
)

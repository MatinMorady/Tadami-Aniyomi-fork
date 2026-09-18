package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * One saved album of a feed source (albums collection feature): the source's category id
 * (an album slug for balbums-like sources) plus the name/cover cached at add time so the
 * library renders without a network round trip.
 */
data class ReelsAlbum(
    val sourceId: Long,
    val albumId: String,
    val name: String,
    val coverUrl: String?,
    val addedAt: Date,
)

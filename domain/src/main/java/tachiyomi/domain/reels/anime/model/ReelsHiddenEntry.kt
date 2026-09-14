package tachiyomi.domain.reels.anime.model

import java.util.Date

/**
 * One local "not interested" decision (B3.2): hides a reel or an author inside a source's feed.
 * Host-side only — the feed contract is untouched. Kind is [KIND_VIDEO] or [KIND_AUTHOR].
 * [label] is a human-readable title captured at hide time (the manager screen falls back to
 * [value] when it is null — legacy rows and author entries).
 */
data class ReelsHiddenEntry(
    val sourceId: Long,
    val kind: String,
    val value: String,
    val hiddenAt: Date,
    val label: String? = null,
) {
    companion object {
        const val KIND_VIDEO = "video"
        const val KIND_AUTHOR = "author"
    }
}

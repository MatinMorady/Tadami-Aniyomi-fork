package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem

/**
 * Real offline copies of favorite reels (B3.3): the file lives in the app-private
 * filesDir/reels_offline and the offline playlist prefers it over the network URL. Names are
 * reversible (`sourceId_<Uri-encoded videoId>.<ext>`) so the stored set can be listed without
 * side metadata.
 */
interface ReelsOfflineStore {

    fun isStored(sourceId: Long, videoId: String): Boolean

    /** Playable file:// URL of a stored copy, or null. */
    fun localUrl(sourceId: Long, videoId: String): String?

    /** All stored (sourceId, videoId) pairs. */
    fun storedPairs(): Set<Pair<Long, String>>

    /**
     * Downloads the reel's base URL to the store. False on transport failure or when the copy
     * would push the store past [MAX_OFFLINE_BYTES].
     */
    suspend fun download(item: ShortVideoItem, sourceId: Long): Boolean

    suspend fun delete(sourceId: Long, videoId: String): Boolean

    companion object {
        // Same ceiling as the playback cache: the store never grows without bound.
        const val MAX_OFFLINE_BYTES = 1_000_000_000L
    }
}

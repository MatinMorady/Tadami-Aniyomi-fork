package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry

interface ReelsWatchRepository {

    fun subscribeAll(): Flow<List<ReelsWatchEntry>>

    suspend fun getByVideo(videoId: String, sourceId: Long): ReelsWatchEntry?

    suspend fun upsert(entry: ReelsWatchEntry)

    suspend fun delete(videoId: String, sourceId: Long)

    /** Clear the whole history (user-initiated). */
    suspend fun deleteAll()
}

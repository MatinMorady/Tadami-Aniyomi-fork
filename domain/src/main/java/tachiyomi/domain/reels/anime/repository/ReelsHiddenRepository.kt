package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry

interface ReelsHiddenRepository {

    /** Live list for the hidden-content manager screen (audit H7), newest first. */
    fun subscribeAll(): Flow<List<ReelsHiddenEntry>>

    suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry>

    suspend fun insert(entry: ReelsHiddenEntry)

    suspend fun delete(sourceId: Long, kind: String, value: String)

    /** Removes every hidden entry of one source (cleanup cascade for uninstalled sources). */
    suspend fun deleteBySource(sourceId: Long)

    /** Unhide everything (user-initiated from the manager screen). */
    suspend fun deleteAll()
}

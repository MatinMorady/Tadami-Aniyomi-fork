package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.reels.anime.model.ReelsFavorite

interface ReelsFavoriteRepository {

    fun subscribeAll(): Flow<List<ReelsFavorite>>

    suspend fun getAll(): List<ReelsFavorite>

    suspend fun getBySource(sourceId: Long): List<ReelsFavorite>

    suspend fun getIdsBySource(sourceId: Long): List<String>

    suspend fun insert(favorite: ReelsFavorite)

    suspend fun insertAll(favorites: List<ReelsFavorite>)

    suspend fun delete(videoId: String, sourceId: Long)

    /**
     * Removes every saved reel of one source. Used for the user-initiated cleanup of rows
     * whose source was uninstalled (the app keeps user data, so nothing is auto-deleted).
     */
    suspend fun deleteBySource(sourceId: Long)
}

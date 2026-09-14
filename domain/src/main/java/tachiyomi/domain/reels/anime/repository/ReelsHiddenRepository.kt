package tachiyomi.domain.reels.anime.repository

import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry

interface ReelsHiddenRepository {

    suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry>

    suspend fun insert(entry: ReelsHiddenEntry)

    suspend fun delete(sourceId: Long, kind: String, value: String)
}

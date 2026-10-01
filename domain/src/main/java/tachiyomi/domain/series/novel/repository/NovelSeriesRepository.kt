package tachiyomi.domain.series.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry

interface NovelSeriesRepository {
    fun getAllSeries(): Flow<List<NovelSeries>>
    fun getSeriesById(id: Long): Flow<NovelSeries?>
    fun getSeriesByCategory(categoryId: Long): Flow<List<NovelSeries>>
    suspend fun getSeriesForNovel(novelId: Long): NovelSeries?
    fun getEntriesForSeries(seriesId: Long): Flow<List<NovelSeriesEntry>>
    fun getLibrarySeriesWithEntries(): Flow<List<LibraryNovelSeries>>
    fun getAllNovelIdsInAnySeries(): Flow<Set<Long>>
    suspend fun insertSeries(series: NovelSeries): Long
    suspend fun updateSeries(series: NovelSeries)
    suspend fun deleteSeries(seriesId: Long)

    /**
     * Moves all series assigned to [categoryId] back to the default category (0). Called when a
     * category is deleted: novel_series.category_id has no FK, so without this the series keep a
     * dangling id and become invisible on every library page with no way back.
     */
    suspend fun moveSeriesFromCategoryToDefault(categoryId: Long)

    suspend fun insertEntry(entry: NovelSeriesEntry)
    suspend fun deleteEntry(novelId: Long)
    suspend fun updateEntryPositions(entries: List<NovelSeriesEntry>)
}

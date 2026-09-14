package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_hidden
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsHiddenRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsHiddenRepository {

    override fun subscribeAll(): Flow<List<ReelsHiddenEntry>> {
        return handler.subscribeToList { db ->
            db.reels_hiddenQueries.getAll()
        }.map { rows -> rows.map(::mapEntry) }
    }

    override suspend fun getBySource(sourceId: Long): List<ReelsHiddenEntry> {
        return handler.awaitList { db ->
            db.reels_hiddenQueries.getBySource(sourceId)
        }.map(::mapEntry)
    }

    override suspend fun insert(entry: ReelsHiddenEntry) {
        try {
            handler.await { db -> insert(db, entry) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels hide ${entry.kind}:${entry.value}" }
        }
    }

    override suspend fun delete(sourceId: Long, kind: String, value: String) {
        try {
            handler.await { db ->
                db.reels_hiddenQueries.delete(sourceId, kind, value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels hide $kind:$value" }
        }
    }

    override suspend fun deleteBySource(sourceId: Long) {
        try {
            handler.await { db ->
                db.reels_hiddenQueries.deleteBySource(sourceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels hides of source $sourceId" }
        }
    }

    override suspend fun deleteAll() {
        try {
            handler.await { db ->
                db.reels_hiddenQueries.deleteAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to clear reels hides" }
        }
    }

    private fun insert(db: AnimeDb, entry: ReelsHiddenEntry) {
        db.reels_hiddenQueries.insert(
            source_id = entry.sourceId,
            kind = entry.kind,
            hidden_value = entry.value,
            label = entry.label,
            hidden_at = entry.hiddenAt,
        )
    }

    private fun mapEntry(row: Reels_hidden): ReelsHiddenEntry {
        return ReelsHiddenEntry(
            sourceId = row.source_id,
            kind = row.kind,
            value = row.hidden_value,
            hiddenAt = row.hidden_at,
            label = row.label,
        )
    }
}

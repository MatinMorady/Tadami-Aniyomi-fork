package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_hidden
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsHiddenRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsHiddenRepository {

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

    private fun insert(db: AnimeDb, entry: ReelsHiddenEntry) {
        db.reels_hiddenQueries.insert(
            source_id = entry.sourceId,
            kind = entry.kind,
            hidden_value = entry.value,
            hidden_at = entry.hiddenAt,
        )
    }

    private fun mapEntry(row: Reels_hidden): ReelsHiddenEntry {
        return ReelsHiddenEntry(
            sourceId = row.source_id,
            kind = row.kind,
            value = row.hidden_value,
            hiddenAt = row.hidden_at,
        )
    }
}

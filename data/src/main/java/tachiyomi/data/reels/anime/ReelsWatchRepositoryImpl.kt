package tachiyomi.data.reels.anime

import android.database.sqlite.SQLiteException
import dataanime.Reels_watch_history
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

class ReelsWatchRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : ReelsWatchRepository {

    override fun subscribeAll(): Flow<List<ReelsWatchEntry>> {
        return handler.subscribeToList { db ->
            db.reels_watch_historyQueries.getAll()
        }.map { rows -> rows.map(::mapEntry) }
    }

    override suspend fun getByVideo(videoId: String, sourceId: Long): ReelsWatchEntry? {
        return handler.awaitOneOrNull { db ->
            db.reels_watch_historyQueries.getByVideo(videoId, sourceId)
        }?.let(::mapEntry)
    }

    override suspend fun upsert(entry: ReelsWatchEntry) {
        // History writes are fire-and-forget (the feed must never stall on a DB hiccup), but a
        // cancellation must not be swallowed (structured concurrency, same rule as favorites).
        try {
            handler.await(inTransaction = true) { db ->
                insert(db, entry)
                // Cap the table: history is a convenience surface, not an archive — unbounded
                // growth would slow the grid and the DB forever.
                db.reels_watch_historyQueries.prune(MAX_HISTORY_ROWS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to save reels watch entry ${entry.videoId}" }
        }
    }

    override suspend fun delete(videoId: String, sourceId: Long) {
        try {
            handler.await { db ->
                db.reels_watch_historyQueries.delete(videoId, sourceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to delete reels watch entry $videoId" }
        }
    }

    override suspend fun deleteAll() {
        try {
            handler.await { db ->
                db.reels_watch_historyQueries.deleteAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to clear reels watch history" }
        }
    }

    private fun insert(db: AnimeDb, entry: ReelsWatchEntry) {
        db.reels_watch_historyQueries.upsert(
            video_id = entry.videoId,
            source_id = entry.sourceId,
            title = entry.title,
            author = entry.author,
            poster_url = entry.posterUrl,
            web_url = entry.webUrl,
            video_url = entry.videoUrl,
            duration_sec = entry.durationSec,
            position_ms = entry.positionMs,
            watched_at = entry.watchedAt,
        )
    }

    private fun mapEntry(row: Reels_watch_history): ReelsWatchEntry {
        return ReelsWatchEntry(
            videoId = row.video_id,
            sourceId = row.source_id,
            title = row.title,
            author = row.author,
            posterUrl = row.poster_url,
            webUrl = row.web_url,
            videoUrl = row.video_url,
            durationSec = row.duration_sec,
            positionMs = row.position_ms,
            watchedAt = row.watched_at,
        )
    }

    private companion object {
        // Generous cap: months of casual watching stay browsable, the grid never chokes.
        const val MAX_HISTORY_ROWS = 500L
    }
}

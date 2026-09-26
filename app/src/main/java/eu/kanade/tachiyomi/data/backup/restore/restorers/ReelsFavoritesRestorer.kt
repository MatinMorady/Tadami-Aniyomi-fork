package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupReelsFavorite
import eu.kanade.tachiyomi.data.backup.models.toReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFavoritesRestorer(
    private val repository: ReelsFavoriteRepository = Injekt.get(),
) {
    // Restore policy (favorites): local state is authoritative — rows that already exist are
    // never overwritten (a like removed locally must not resurrect from the backup). Rows are
    // inserted UNCONDITIONALLY: extension restore only launches the system installer (manual
    // confirmation, completes after this job), so the source manager cannot be consulted here —
    // filtering on it would silently drop every favorite on a fresh install. Dangling rows of
    // still-missing sources are visible in the Favorites screen and removed by the user-initiated
    // "clean up missing sources" action, never automatically.
    // One batched write: the repository persists the whole list in a single transaction.
    suspend fun restoreReelsFavorites(backup: List<BackupReelsFavorite>) {
        if (backup.isEmpty()) return
        val existing = repository.getAll().map { it.videoId to it.sourceId }.toSet()
        val toInsert = backup.filter { it.videoId to it.sourceId !in existing }
        if (toInsert.isEmpty()) return
        repository.insertAll(toInsert.map { it.toReelsFavorite() })
    }
}

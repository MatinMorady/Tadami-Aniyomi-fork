package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupReelsFollow
import eu.kanade.tachiyomi.data.backup.models.toReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFollowsRestorer(
    private val repository: ReelsFollowRepository = Injekt.get(),
) {
    // Restore policy (creator follows): local state is authoritative — rows that already exist
    // are never overwritten (an unfollow performed locally must not resurrect). Rows are inserted
    // UNCONDITIONALLY: extension restore only launches the system installer (manual confirmation,
    // completes after this job), so the source manager cannot be consulted here — filtering on it
    // would silently drop every follow on a fresh install. Follows of still-missing sources stay
    // inert (the FOLLOWING feed simply skips them) until the extension is installed.
    suspend fun restoreReelsFollows(backup: List<BackupReelsFollow>) {
        if (backup.isEmpty()) return
        val existing = repository.getAll().map { it.sourceId to it.creator }.toSet()
        backup.asSequence()
            .filter { it.sourceId to it.creator !in existing }
            .forEach { repository.insert(it.toReelsFollow()) }
    }
}

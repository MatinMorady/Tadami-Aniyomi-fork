package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupReelsFollow
import eu.kanade.tachiyomi.data.backup.models.toBackupReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFollowsBackupCreator(
    private val repository: ReelsFollowRepository = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupReelsFollow> {
        return repository.getAll().map { it.toBackupReelsFollow() }
    }
}

package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTag
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Собирает скрытые тайтлы, теговый блэклист и сигнал-лог вкусов «Для тебя»
 * по всем медиатипам. Кэш подборок не собирается — он регенерируем (решение A-спека).
 */
class DiscoveryBackupCreator(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend operator fun invoke():
        Triple<List<BackupDiscoveryHidden>, List<BackupDiscoveryTag>, List<BackupDiscoverySignal>> {
        val hidden = mutableListOf<BackupDiscoveryHidden>()
        val tags = mutableListOf<BackupDiscoveryTag>()
        val signals = mutableListOf<BackupDiscoverySignal>()
        DiscoveryMediaType.entries.forEach { media ->
            repository.getHiddenEntries(media).forEach { hidden += it.toBackupDiscoveryHidden(media.key) }
            repository.getBlacklistEntries(media).forEach { tags += it.toBackupDiscoveryTag(media.key) }
            repository.getSignals(media).forEach { signals += it.toBackupDiscoverySignal() }
        }
        return Triple(hidden, tags, signals)
    }
}

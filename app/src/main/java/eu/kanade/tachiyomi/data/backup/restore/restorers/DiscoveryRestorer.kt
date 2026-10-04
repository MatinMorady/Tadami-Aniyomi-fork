package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoverySignal
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toDomainEntry
import eu.kanade.tachiyomi.data.backup.models.toDomainSignal
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restore скрытых тайтлов, тегов и сигнал-лога вкусов «Для тебя»: один батч
 * (транзакция) на медиатип, insertOrIgnore/upsert-merge — идемпотентно,
 * существующие локальные записи не затираются.
 */
class DiscoveryRestorer(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend fun restoreDiscovery(
        hidden: List<BackupDiscoveryHidden>,
        tags: List<BackupDiscoveryTag>,
        signals: List<BackupDiscoverySignal> = emptyList(),
    ) {
        hidden.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreHiddenEntries(media, items.map { it.toDomainEntry() })
        }
        tags.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreBlacklistEntries(media, items.map { it.toDomainEntry() })
        }
        if (signals.isNotEmpty()) {
            // Битые ключи (мусорные media/signal) молча пропускаются на маппинге.
            val domainSignals = signals.mapNotNull { it.toDomainSignal() }
            if (domainSignals.isNotEmpty()) {
                repository.restoreSignals(domainSignals)
            }
        }
    }
}

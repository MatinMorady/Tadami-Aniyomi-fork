package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.first
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Taste Learning Engine: wiring записи сигналов взаимодействия.
 * Жанры — best-effort снимок: TASTE-ряд хранит их в reason-CSV; для прочих
 * рядов жанрового вклада нет, но source-аффинити (по sourceId) считается.
 * Сеть в момент сигнала не трогается — обогащение остаётся за генерацией ленты.
 */
object TasteSignalRecorder {

    /** Жанры тайтла из reason-CSV (TASTE) — пусто для прочих рядов. */
    fun snapshotGenres(item: DiscoverySuggestion): List<String> =
        if (item.rowType == DiscoveryRowType.TASTE) {
            item.reason
                ?.splitToSequence(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.toList()
                .orEmpty()
        } else {
            emptyList()
        }

    suspend fun record(
        repository: DiscoveryRepository,
        item: DiscoverySuggestion,
        signalType: DiscoverySignalType,
    ) {
        val sourceKey = resolveSourceKey(item.mediaType, item.sourceId)
        runCatching {
            repository.recordSignal(
                mediaType = item.mediaType,
                cleanTitle = item.cleanTitle,
                title = item.title,
                signalType = signalType,
                genres = snapshotGenres(item),
                provider = item.provider,
                sourceKey = sourceKey,
            )
        }
    }

    /**
     * Ключ плагина для source-аффинити — та же семантика, что у участия плагинов
     * в [DiscoveryRunner.installedPluginsProvider]: pkgName расширения (anime/manga)
     * или pluginId новелл-плагина. Источник без плагина (OmniSource, локальный)
     * ключа не имеет — аффинити для него не считается.
     */
    suspend fun resolveSourceKey(mediaType: DiscoveryMediaType, sourceId: Long?): String? {
        if (sourceId == null || sourceId <= 0L) return null
        return runCatching {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> {
                    val manager = Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    val extensions = Injekt
                        .get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                        .installedExtensionsFlow.first()
                    extensions.firstOrNull { ext -> sourceId in ext.sources.map { it.id } }?.pkgName
                }
                DiscoveryMediaType.MANGA -> {
                    val manager = Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    val extensions = Injekt
                        .get<eu.kanade.tachiyomi.extension.manga.MangaExtensionManager>()
                        .installedExtensionsFlow.first()
                    extensions.firstOrNull { ext -> sourceId in ext.sources.map { it.id } }?.pkgName
                }
                DiscoveryMediaType.NOVEL -> {
                    val manager = Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                    val catalogueIds = manager.getCatalogueSources().mapTo(HashSet()) { it.id }
                    if (sourceId !in catalogueIds) return@runCatching null
                    Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                        .getPluginId(sourceId)
                }
            }
        }.getOrNull()
    }
}

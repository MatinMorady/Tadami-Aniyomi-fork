package eu.kanade.tachiyomi.ui.discovery

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Общий резолвер direct open: materialize plugin-bound тайтла в локальной БД по связке
 * (sourceId, sourceUrl). Используется лентой «Для тебя» и Home-тизерами.
 * Null при неполной привязке, неустановленном (или стаб) источнике и при любой ошибке —
 * вызывающий экран уходит в шторку предпросмотра.
 */
internal class DiscoveryDirectOpenResolver(
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
) {
    suspend fun resolveEntryId(item: DiscoverySuggestion): Long? {
        val isSourceInstalled: (Long) -> Boolean = { sourceId ->
            when (item.mediaType) {
                DiscoveryMediaType.MANGA -> {
                    val source = Injekt.get<MangaSourceManager>().get(sourceId)
                    source != null && source !is StubMangaSource
                }
                DiscoveryMediaType.ANIME -> {
                    val source = Injekt.get<AnimeSourceManager>().get(sourceId)
                    source != null && source !is StubAnimeSource
                }
                DiscoveryMediaType.NOVEL -> {
                    val source = Injekt.get<NovelSourceManager>().get(sourceId)
                    source != null && source !is StubNovelSource
                }
            }
        }
        val decision = decideDirectOpen(item, isSourceInstalled)
        if (decision !is DirectOpenDecision.Open) return null
        return runCatching {
            when (item.mediaType) {
                DiscoveryMediaType.MANGA -> {
                    val manga = SManga.create().apply {
                        url = decision.url
                        title = item.title
                        thumbnail_url = item.coverUrl
                    }
                    networkToLocalManga.await(manga.toDomainManga(decision.sourceId)).id
                }
                DiscoveryMediaType.ANIME -> {
                    val anime = SAnime.create().apply {
                        url = decision.url
                        title = item.title
                        thumbnail_url = item.coverUrl
                    }
                    networkToLocalAnime.await(anime.toDomainAnime(decision.sourceId)).id
                }
                DiscoveryMediaType.NOVEL -> {
                    val novel = SNovel.create().apply {
                        url = decision.url
                        title = item.title
                        thumbnail_url = item.coverUrl
                    }
                    networkToLocalNovel.await(novel.toDomainNovel(decision.sourceId)).id
                }
            }
        }.getOrElse { e ->
            // CancellationException — не «источник упал»: отмену (выход с экрана)
            // пробрасываем, иначе нарушается structured concurrency.
            if (e is CancellationException) throw e
            // Наблюдаемость фолбэка в шторку (включая Error-класс, который сознательно не гасим выше).
            logcat { "[DirectOpen] resolveEntryId failed for «${item.title}»: ${e.message}" }
            null
        }
    }
}

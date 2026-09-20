package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.track.anime.MapAnimeTrackStatusToLibrary
import eu.kanade.domain.track.manga.MapMangaTrackStatusToLibrary
import eu.kanade.domain.track.novel.MapNovelTrackStatusToLibrary
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.first
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.library.model.LibraryTrackStatus
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.novel.interactor.GetTracksPerNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Реальные источники данных для сидов: библиотека (через GetLibrary*-интеракторы,
 * те же, что используют Home-модели) и история по медиатипам.
 */
class AppDiscoverySeedSources : DiscoverySeedSources {

    override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> =
        when (mediaType) {
            DiscoveryMediaType.ANIME -> animeCandidates()
            DiscoveryMediaType.MANGA -> mangaCandidates()
            DiscoveryMediaType.NOVEL -> novelCandidates()
        }

    private suspend fun novelCandidates(): List<DiscoverySeedInput> {
        val droppedIds = droppedNovelIds()
        return Injekt.get<GetLibraryNovel>().await().map { item ->
            val completed = item.totalChapters > 0 && item.readCount >= item.totalChapters
            DiscoverySeedInput(
                entryId = item.novel.id,
                title = item.novel.title,
                sourceId = item.novel.source,
                description = item.novel.description,
                author = item.novel.author,
                genres = item.novel.genre.orEmpty(),
                dateAdded = item.novel.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastRead.takeIf { it > 0 } else null,
                lastInteraction = item.lastRead.takeIf { it > 0 },
                isDropped = item.novel.id in droppedIds,
            )
        }
    }

    private suspend fun mangaCandidates(): List<DiscoverySeedInput> {
        val droppedIds = droppedMangaIds()
        return Injekt.get<GetLibraryManga>().await().map { item ->
            val completed = item.totalChapters > 0 && item.readCount >= item.totalChapters
            DiscoverySeedInput(
                entryId = item.manga.id,
                title = item.manga.title,
                sourceId = item.manga.source,
                description = item.manga.description,
                author = item.manga.author,
                genres = item.manga.genre.orEmpty(),
                dateAdded = item.manga.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastRead.takeIf { it > 0 } else null,
                lastInteraction = item.lastRead.takeIf { it > 0 },
                isDropped = item.manga.id in droppedIds,
            )
        }
    }

    private suspend fun animeCandidates(): List<DiscoverySeedInput> {
        val droppedIds = droppedAnimeIds()
        return Injekt.get<GetLibraryAnime>().await().map { item ->
            val completed = item.totalCount > 0 && item.seenCount >= item.totalCount
            DiscoverySeedInput(
                entryId = item.anime.id,
                title = item.anime.title,
                sourceId = item.anime.source,
                description = item.anime.description,
                author = item.anime.author,
                genres = item.anime.genre.orEmpty(),
                dateAdded = item.anime.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastSeen.takeIf { it > 0 } else null,
                lastInteraction = item.lastSeen.takeIf { it > 0 },
                isDropped = item.anime.id in droppedIds,
            )
        }
    }

    // DROPPED берётся только из трекинг-сервисов (как BY_TRACK_STATUS в библиотеке):
    // displayStatus живёт в пространстве SManga/SNovel/SAnime, где 5 == CANCELLED.
    private suspend fun droppedNovelIds(): Set<Long> {
        val trackMapper = MapNovelTrackStatusToLibrary(Injekt.get<TrackerManager>())
        return Injekt.get<GetTracksPerNovel>().subscribe().first()
            .filterValues { tracks ->
                tracks.any { trackMapper.map(it.trackerId, it.status) == LibraryTrackStatus.DROPPED }
            }
            .keys
    }

    private suspend fun droppedMangaIds(): Set<Long> {
        val trackMapper = MapMangaTrackStatusToLibrary(Injekt.get<TrackerManager>())
        return Injekt.get<GetTracksPerManga>().subscribe().first()
            .filterValues { tracks ->
                tracks.any { trackMapper.map(it.trackerId, it.status) == LibraryTrackStatus.DROPPED }
            }
            .keys
    }

    private suspend fun droppedAnimeIds(): Set<Long> {
        val trackMapper = MapAnimeTrackStatusToLibrary(Injekt.get<TrackerManager>())
        return Injekt.get<GetTracksPerAnime>().subscribe().first()
            .filterValues { tracks ->
                tracks.any { trackMapper.map(it.trackerId, it.status) == LibraryTrackStatus.DROPPED }
            }
            .keys
    }

    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType): Set<String> {
        val titles = when (mediaType) {
            DiscoveryMediaType.ANIME -> Injekt.get<AnimeHistoryRepository>()
                .getAnimeHistory("").first().map { it.title }
            DiscoveryMediaType.MANGA -> Injekt.get<MangaHistoryRepository>()
                .getMangaHistory("").first().map { it.title }
            DiscoveryMediaType.NOVEL -> Injekt.get<NovelHistoryRepository>()
                .getNovelHistory("").first().map { it.title }
        }
        return titles.mapTo(HashSet()) { normalizeDiscoveryTitle(it) }
    }
}

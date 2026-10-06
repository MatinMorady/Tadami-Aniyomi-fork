package tachiyomi.domain.entries.anime.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import kotlin.time.Duration.Companion.seconds

class GetLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getLibraryAnime()
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeAsFlow()
            .retry(MAX_NPE_RETRIES) { cause ->
                // Cursor-mapper NPEs (library view) are retried a few times for transient races,
                // but never forever: the unbounded loop re-queried the whole library every 0.5s
                // indefinitely on persistent row states and the section silently never loaded
                // (manga/novel parity, see GetLibraryManga). A persistent failure falls to catch.
                if (cause is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryAnime.logcat(LogPriority.ERROR, it)
            }
    }

    fun subscribeRecent(limit: Long): Flow<List<LibraryAnime>> {
        return animeRepository.getRecentLibraryAnime(limit)
            .retry(MAX_NPE_RETRIES) { cause ->
                // Cursor-mapper NPEs (library view) are retried a few times for transient races,
                // but never forever: the unbounded loop re-queried the whole library every 0.5s
                // indefinitely on persistent row states and the section silently never loaded
                // (manga/novel parity, see GetLibraryManga). A persistent failure falls to catch.
                if (cause is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryAnime.logcat(LogPriority.ERROR, it)
            }
    }

    fun subscribeRecentFavorites(limit: Long): Flow<List<Anime>> {
        return animeRepository.getRecentFavorites(limit)
            .retry(MAX_NPE_RETRIES) { cause ->
                // Cursor-mapper NPEs (library view) are retried a few times for transient races,
                // but never forever: the unbounded loop re-queried the whole library every 0.5s
                // indefinitely on persistent row states and the section silently never loaded
                // (manga/novel parity, see GetLibraryManga). A persistent failure falls to catch.
                if (cause is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryAnime.logcat(LogPriority.ERROR, it)
            }
    }

    private companion object {
        const val MAX_NPE_RETRIES = 3L
    }
}

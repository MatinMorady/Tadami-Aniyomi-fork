package tachiyomi.domain.entries.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getLibraryManga()
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
            .retry(MAX_NPE_RETRIES) { cause ->
                // Cursor-mapper NPEs (library view, see crash logs) are retried a few times for
                // transient races, but never forever: on devices where the offending row state
                // persisted this loop re-queried the whole library every 0.5s indefinitely and
                // the section silently never loaded. A persistent failure falls to catch below.
                if (cause is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }

    private companion object {
        const val MAX_NPE_RETRIES = 3L
    }
}

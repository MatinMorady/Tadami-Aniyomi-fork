package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsWatchHistoryScreenModel(
    private val repository: ReelsWatchRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<ReelsWatchHistoryScreenModel.State>(State()) {

    @Immutable
    data class State(
        val entries: List<ReelsWatchEntry> = emptyList(),
        val sourceNames: Map<Long, String> = emptyMap(),
    )

    init {
        screenModelScope.launch {
            repository.subscribeAll().collectLatest { entries ->
                val names = entries
                    .map { it.sourceId }
                    .distinct()
                    .mapNotNull { id -> sourceManager.get(id)?.let { id to it.name } }
                    .toMap()
                mutableState.update { it.copy(entries = entries, sourceNames = names) }
            }
        }
    }

    suspend fun clearHistory() {
        repository.deleteAll()
    }

    /** Single-entry removal (audit H10): long-press on a history cell. */
    fun remove(entry: ReelsWatchEntry) {
        screenModelScope.launch {
            repository.delete(entry.videoId, entry.sourceId)
        }
    }
}

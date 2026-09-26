package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.domain.reels.anime.repository.ReelsHiddenRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Hidden-content manager (audit H7): "not interested" decisions used to be reversible only
 * through the transient undo snackbar. Local data only — the list is live (repository flow),
 * unhiding a row removes it and the feeds stop filtering it on the next page/source switch.
 */
class ReelsHiddenScreenModel(
    private val repository: ReelsHiddenRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<ReelsHiddenScreenModel.State>(State()) {

    @Immutable
    data class State(
        val entries: List<ReelsHiddenEntry> = emptyList(),
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

    fun unhide(entry: ReelsHiddenEntry) {
        screenModelScope.launch {
            repository.delete(entry.sourceId, entry.kind, entry.value)
        }
    }

    fun unhideAll() {
        screenModelScope.launch {
            repository.deleteAll()
        }
    }
}

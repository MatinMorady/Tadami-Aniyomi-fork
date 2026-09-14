package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReelsFavoritesScreenModel(
    private val repository: ReelsFavoriteRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<ReelsFavoritesScreenModel.State>(State()) {

    @Immutable
    data class State(
        val favorites: List<ReelsFavorite> = emptyList(),
        val sourceNames: Map<Long, String> = emptyMap(),
    )

    init {
        screenModelScope.launch {
            repository.subscribeAll().collectLatest { favorites ->
                val names = favorites
                    .map { it.sourceId }
                    .distinct()
                    .mapNotNull { id -> sourceManager.get(id)?.let { id to it.name } }
                    .toMap()
                mutableState.update { it.copy(favorites = favorites, sourceNames = names) }
            }
        }
    }

    fun removeFavorite(favorite: ReelsFavorite) {
        screenModelScope.launch {
            repository.delete(favorite.videoId, favorite.sourceId)
        }
    }

    // Undo path for the remove snackbar: INSERT OR REPLACE restores the row with its
    // original addedAt, so the favorite keeps its sort position.
    fun restoreFavorite(favorite: ReelsFavorite) {
        screenModelScope.launch {
            repository.insert(favorite)
        }
    }

    /**
     * User-initiated cleanup of rows whose source is no longer installed (or is no longer a
     * feed source): the app never auto-deletes user data on extension uninstall. Returns the
     * number of removed rows for the confirmation snackbar.
     */
    suspend fun cleanupMissingSources(): Int {
        // Cold-start guard: the source map is EMPTY until the extension subsystem reports
        // ready — proceeding immediately would classify every source as missing and wipe the
        // whole favorites list on a confirmed cleanup.
        sourceManager.isInitialized.first { it }
        val favorites = repository.getAll()
        val missingSourceIds = favorites.map { it.sourceId }.toSet().filter { sourceId ->
            sourceManager.get(sourceId) !is AnimeFeedSource
        }
        if (missingSourceIds.isEmpty()) return 0
        val removedCount = favorites.count { it.sourceId in missingSourceIds }
        missingSourceIds.forEach { sourceId -> repository.deleteBySource(sourceId) }
        return removedCount
    }
}

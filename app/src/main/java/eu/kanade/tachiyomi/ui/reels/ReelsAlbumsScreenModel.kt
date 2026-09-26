package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Albums collection library of one source (device feature, sign-off prototype): the saved
 * albums (local storage, no network) plus the source's category catalog for adding new ones
 * (AnimeFeedBrowseSource.getBrowseCategories — the same data the niches screen shows).
 */
class ReelsAlbumsScreenModel(
    val sourceId: Long,
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsAlbumsScreenModel.State>(State()) {

    @Immutable
    data class State(
        val albums: ImmutableList<ReelsAlbum> = persistentListOf(),
        val catalog: ImmutableList<FeedCategory> = persistentListOf(),
        val isCatalogLoading: Boolean = false,
        val catalogError: String? = null,
    )

    init {
        screenModelScope.launch {
            albumRepository.subscribeBySource(sourceId).collectLatest { albums ->
                mutableState.update { it.copy(albums = albums.toImmutableList()) }
            }
        }
        loadCatalog()
    }

    /** Page 1 of the source's category directory; the add-sheet lists it with saved marks. */
    fun loadCatalog() {
        val browse = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (browse == null) {
            mutableState.update { it.copy(catalogError = "Source unavailable", isCatalogLoading = false) }
            return
        }
        mutableState.update { it.copy(isCatalogLoading = true, catalogError = null) }
        screenModelScope.launch(ioDispatcher) {
            try {
                val page = browse.getBrowseCategories(1, null)
                mutableState.update {
                    it.copy(catalog = page.categories.toImmutableList(), isCatalogLoading = false)
                }
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, throwable = t) { "Failed to load albums catalog of $sourceId" }
                mutableState.update {
                    it.copy(catalogError = t.localizedMessage.orEmpty(), isCatalogLoading = false)
                }
            }
        }
    }

    fun addAlbum(category: FeedCategory) {
        screenModelScope.launch(ioDispatcher) {
            albumRepository.insert(
                ReelsAlbum(
                    sourceId = sourceId,
                    albumId = category.id,
                    name = category.name,
                    coverUrl = category.imageUrl,
                    addedAt = Date(),
                ),
            )
        }
    }

    fun removeAlbum(album: ReelsAlbum) {
        screenModelScope.launch(ioDispatcher) {
            albumRepository.delete(sourceId, album.albumId)
        }
    }
}

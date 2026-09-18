package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
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
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * One saved album's content (device feature, sign-off prototype): paged items of the source's
 * category feed rendered as a preview grid; progress bars and watched badges come from the
 * watch history (positionMs/durationSec) plus the manual watched marks; the search field is a
 * client-side filter over the loaded pages (the site offers no server-side album search).
 * Tapping an item opens the regular reels feed in niche mode at that clip.
 */
class ReelsAlbumScreenModel(
    val sourceId: Long,
    val albumId: String,
    private val albumName: String = "",
    private val albumRepository: ReelsAlbumRepository = Injekt.get(),
    private val watchRepository: ReelsWatchRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsAlbumScreenModel.State>(State()) {

    @Immutable
    data class State(
        val items: ImmutableList<ShortVideoItem> = persistentListOf(),
        val isLoading: Boolean = false,
        val canLoadMore: Boolean = true,
        val error: String? = null,
        val watched: Set<String> = emptySet(),
        val progress: Map<String, Float> = emptyMap(),
        // Report fix: the AppBar save toggle observes the collection reactively.
        val isAlbumSaved: Boolean = false,
    )

    private var nextPage = 1
    private var nextCursor: String? = null
    private val seenIds = mutableSetOf<String>()

    init {
        screenModelScope.launch {
            albumRepository.subscribeBySource(sourceId).collectLatest { albums ->
                mutableState.update { it.copy(isAlbumSaved = albums.any { a -> a.albumId == albumId }) }
            }
        }
        screenModelScope.launch {
            albumRepository.subscribeWatched(sourceId).collectLatest { watched ->
                mutableState.update { it.copy(watched = watched) }
            }
        }
        screenModelScope.launch {
            watchRepository.subscribeAll().collectLatest { entries ->
                val progress = entries
                    .filter { it.sourceId == sourceId }
                    .mapNotNull { entry ->
                        val durationMs = (entry.durationSec ?: 0.0) * 1000.0
                        if (durationMs <= 0.0) {
                            null
                        } else {
                            entry.videoId to (entry.positionMs / durationMs).toFloat().coerceIn(0f, 1f)
                        }
                    }
                    .toMap()
                mutableState.update { it.copy(progress = progress) }
            }
        }
        loadMore()
    }

    /** Appends the next category-feed page; sticky-cursor protocol per contract v17. */
    fun loadMore() {
        if (state.value.isLoading || !state.value.canLoadMore) return
        val browse = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (browse == null) {
            mutableState.update { it.copy(error = "Source unavailable", canLoadMore = false) }
            return
        }
        mutableState.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch(ioDispatcher) {
            try {
                val page = browse.getCategoryFeed(albumId, nextPage, nextCursor)
                val fresh = page.videos.filter { seenIds.add(it.id) }
                nextPage++
                nextCursor = page.nextCursor
                mutableState.update { current ->
                    current.copy(
                        items = (current.items + fresh).toImmutableList(),
                        isLoading = false,
                        canLoadMore = page.hasNextPage,
                    )
                }
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, throwable = t) { "Failed to load album $albumId page $nextPage" }
                mutableState.update { it.copy(isLoading = false, error = t.localizedMessage.orEmpty()) }
            }
        }
    }

    fun markWatched(videoId: String) {
        screenModelScope.launch(ioDispatcher) { albumRepository.markWatched(sourceId, videoId) }
    }

    fun unmarkWatched(videoId: String) {
        screenModelScope.launch(ioDispatcher) { albumRepository.unmarkWatched(sourceId, videoId) }
    }

    /** AppBar save/unsave of this album (report fix); cover = first reel's poster. */
    fun toggleAlbumSaved() {
        screenModelScope.launch(ioDispatcher) {
            if (state.value.isAlbumSaved) {
                albumRepository.delete(sourceId, albumId)
            } else {
                albumRepository.insert(
                    ReelsAlbum(
                        sourceId = sourceId,
                        albumId = albumId,
                        name = albumName,
                        coverUrl = state.value.items.firstOrNull()?.posterUrl,
                        addedAt = Date(),
                    ),
                )
            }
        }
    }
}

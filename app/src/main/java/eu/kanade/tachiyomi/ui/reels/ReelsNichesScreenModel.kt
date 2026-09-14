package eu.kanade.tachiyomi.ui.reels

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicInteger

/**
 * Category directory of one feed source (contract v20, AnimeFeedBrowseSource): paginated
 * rows the Niches screen renders as a preview grid. Pagination follows the v17 sticky
 * protocol on its own stream. A source without the capability (or a vanished source) yields
 * an empty terminal state, not an error.
 */
class ReelsNichesScreenModel(
    private val sourceId: Long,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateScreenModel<ReelsNichesScreenModel.State>(ReelsNichesScreenModel.State()) {

    data class State(
        val categories: ImmutableList<FeedCategory> = persistentListOf(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Transient append failure while the grid is non-empty: kept surfable as a snackbar
        // instead of silently cutting pagination (the error state owns the empty grid only).
        val pageError: String? = null,
        val canLoadMore: Boolean = true,
        val nextPage: Int = 1,
        val nextCursor: String? = null,
        val cursorMode: Boolean = false,
    )

    // Monotonic generation token (the same pattern as ReelsFeedScreenModel): every retry and
    // every loadMore bumps it, and a superseded attempt must not append its page or clobber
    // the cursor of a fresh generation — closing the retry/loadMore race.
    private val loadGeneration = AtomicInteger(0)

    @Volatile
    private var loading = false
    private var loadJob: Job? = null

    init {
        loadMore()
    }

    /** Resets the stream and starts over (error state retry). */
    fun retry() {
        loadGeneration.incrementAndGet()
        loadJob?.cancel()
        loading = false
        mutableState.update { State() }
        loadMore()
    }

    fun loadMore() {
        if (loading || !state.value.canLoadMore) return
        val src = sourceManager.get(sourceId) as? AnimeFeedBrowseSource
        if (src == null) {
            mutableState.update { it.copy(isLoading = false, canLoadMore = false) }
            return
        }
        loading = true
        val generation = loadGeneration.incrementAndGet()
        // Snapshot the pagination inputs on the calling (main) thread.
        val snapshot = state.value
        val page = snapshot.nextPage
        val cursor = if (snapshot.cursorMode) snapshot.nextCursor else null
        loadJob = screenModelScope.launch(ioDispatcher) {
            val result = runCatching { src.getBrowseCategories(page, cursor) }
            // Structured concurrency: a cancellation (screen disposal / superseding load)
            // must propagate, not be laundered into a "feed error" state write.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (loadGeneration.get() == generation) loading = false
            mutableState.update { state ->
                // A retry() that raced this page must own the state: drop the stale write.
                if (loadGeneration.get() != generation) return@update state
                val pageData = result.getOrNull()
                if (pageData == null) {
                    val message = result.exceptionOrNull()?.localizedMessage
                    if (state.categories.isEmpty()) {
                        state.copy(isLoading = false, canLoadMore = false, error = message)
                    } else {
                        // Mid-feed failure: keep the grid usable; scrolling away and back to
                        // the bottom re-arms pagination, and the error is transient.
                        state.copy(isLoading = false, pageError = message, canLoadMore = true)
                    }
                } else {
                    state.copy(
                        isLoading = false,
                        error = null,
                        pageError = null,
                        categories = (state.categories + pageData.categories).toImmutableList(),
                        canLoadMore = pageData.hasNextPage,
                        nextPage = page + 1,
                        nextCursor = pageData.nextCursor,
                        cursorMode = state.cursorMode || pageData.nextCursor != null,
                    )
                }
            }
        }
    }

    fun onPageErrorShown() {
        mutableState.update { it.copy(pageError = null) }
    }
}

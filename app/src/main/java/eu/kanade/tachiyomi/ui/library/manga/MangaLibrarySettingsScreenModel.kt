package eu.kanade.tachiyomi.ui.library.manga

import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaLibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setMangaDisplayMode: SetMangaDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForMangaCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    private val achievementHandler: AchievementHandler = Injekt.get(),
) : ScreenModel {

    /**
     * Owned process-lifetime scope - deliberately NOT Voyager's screenModelScope store
     * dependency. This model is held by tab data objects for the whole process (J1), but
     * ScreenModelStore keys the scope of an UNREGISTERED model under lastScreenModelKey -
     * whichever screen model was remembered last app-wide (e.g. a pushed MangaScreen). When
     * that screen pops, the store's prefix sweep cancels the borrowed scope: every pipeline
     * and preference collector of this model dies silently, later gate re-writes are no-ops
     * and the library section is stuck on LoadingScreen until a process restart (the v0.62.8
     * "Manga section spins forever after finishing a manhwa" report). This member shadows the
     * imported extension for the whole class; same pattern as ReaderSettingsScreenModel.
     * Regression net: LibrarySharedModelScopeTest.
     */
    private val screenModelScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("MangaLibrarySettingsScreenModel"),
    )

    override fun onDispose() {
        screenModelScope.cancel()
    }

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
        achievementHandler.trackFeatureUsed(AchievementEvent.Feature.FILTER)
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedManga(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setMangaDisplayMode.await(mode)
    }

    // D4: serialize setSort - the interactor writes the sort preference BEFORE suspending on
    // the category-flags DB update, so concurrent launches (fast taps on sort options) could
    // finish out of order and leave the preference and the library order permanently desynced.
    private val sortMutex = Mutex()

    fun setSort(
        category: Category?,
        mode: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            sortMutex.withLock {
                setSortModeForCategory.await(category, mode, direction)
            }
        }
    }
}

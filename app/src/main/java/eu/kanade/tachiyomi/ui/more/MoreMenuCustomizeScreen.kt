package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.MoreMenuCustomizeContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as preferenceCollectAsState

/**
 * Order and visibility of the entries on the Aurora «More» screen.
 *
 * [availableIds] is captured by the caller because only `MoreTab` knows which conditional entries
 * (Reels, lattice grid, debug rows) exist right now. [movedTabTitle] is passed as a resolved string:
 * `navStyle.moreTab.options` may only be read inside the bottom-nav tab navigator.
 */
data class MoreMenuCustomizeScreen(
    val availableIds: Set<MoreEntryId>,
    val movedTabTitle: String,
) : Screen {

    override val key: String
        get() = "MoreMenuCustomizeScreen"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MoreMenuCustomizeScreenModel(availableIds) }
        val state by model.state.collectAsStateWithLifecycle()
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val navStyle by uiPreferences.navStyle().preferenceCollectAsState()

        MoreMenuCustomizeContent(
            visible = state.visible,
            hidden = state.hidden,
            movedTabTitle = movedTabTitle,
            movedTabIcon = navStyle.moreIcon,
            onMove = model::move,
            onToggleHidden = model::toggleHidden,
            onReset = model::reset,
            navigateUp = { navigator.pop() },
        )
    }
}

data class MoreMenuCustomizeState(
    val visible: List<MoreEntryId> = emptyList(),
    val hidden: List<MoreEntryId> = emptyList(),
)

/**
 * Reads and writes the two «More» menu preferences. This screen is their only writer, so the state is
 * republished synchronously after every change instead of round-tripping through the preference flow.
 */
class MoreMenuCustomizeScreenModel(
    private val availableIds: Set<MoreEntryId>,
    uiPreferences: UiPreferences = Injekt.get(),
) : ScreenModel {

    private val orderPreference = uiPreferences.moreMenuOrder()
    private val hiddenPreference = uiPreferences.moreMenuHidden()

    private val mutableState = MutableStateFlow(load())

    val state: StateFlow<MoreMenuCustomizeState> = mutableState.asStateFlow()

    fun move(from: Int, to: Int) {
        val current = mutableState.value
        val moved = moveMoreMenuEntry(current.visible, from, to)
        if (moved == current.visible) return

        orderPreference.set(serializeMoreMenuOrder(moved + current.hidden))
        publish()
    }

    fun toggleHidden(id: MoreEntryId) {
        if (id.isPinned) return

        val current = mutableState.value
        val hiddenNames = hiddenPreference.get().toMutableSet()
        if (id in current.hidden) {
            hiddenNames.remove(id.name)
            // A restored entry lands at the end of the visible list.
            orderPreference.set(
                serializeMoreMenuOrder(current.visible + id + current.hidden.filterNot { it == id }),
            )
        } else {
            hiddenNames.add(id.name)
        }
        hiddenPreference.set(hiddenNames)
        publish()
    }

    fun reset() {
        orderPreference.set("")
        hiddenPreference.set(emptySet())
        publish()
    }

    private fun publish() {
        mutableState.value = load()
    }

    private fun load(): MoreMenuCustomizeState {
        val layout = resolveMoreMenuLayout(
            available = availableIds,
            savedOrderRaw = orderPreference.get(),
            hiddenRaw = hiddenPreference.get(),
        )
        return MoreMenuCustomizeState(visible = layout.visible, hidden = layout.hidden)
    }
}

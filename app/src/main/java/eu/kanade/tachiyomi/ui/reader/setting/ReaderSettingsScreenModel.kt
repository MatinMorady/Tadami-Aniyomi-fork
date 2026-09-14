package eu.kanade.tachiyomi.ui.reader.setting

import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsScreenModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val hasDisplayCutout: Boolean,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val onSetSeriesViewerOverride: (Boolean) -> Unit = {},
    val isSeriesViewerOverrideEnabled: () -> Boolean = { false },
    /**
     * Reading mode the viewer actually runs on: series flags, auto-detected webtoon, or the global
     * default. Needed so the pickers highlight the live mode instead of the global default.
     */
    val resolvedReadingMode: () -> ReadingMode = { ReadingMode.DEFAULT },
    val preferences: ReaderPreferences = Injekt.get(),
) : ScreenModel {

    /**
     * Owned scope, cancelled in [onDispose]. This screen model is constructed with a plain
     * remember{} inside the reader composition and is never registered with Voyager's
     * ScreenModelStore, so the shared ioCoroutineScope dependency was never disposed for it:
     * stateIn(Lazily) below kept collecting ReaderViewModel.state forever, leaking the whole
     * reader graph (Activity + Viewer + Views) after the first mode/orientation dialog.
     * Unregistered screen models also all resolve to the SAME "standalone" dependency key in
     * ScreenModelStore, sharing one global scope - another reason to own this one.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("ReaderSettingsScreenModel"),
    )

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Lazily, null)

    override fun onDispose() {
        scope.cancel()
    }
}

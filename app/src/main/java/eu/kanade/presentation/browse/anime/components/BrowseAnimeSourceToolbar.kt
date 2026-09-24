package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.AuroraBackLens
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun BrowseAnimeSourceToolbar(
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    source: AnimeSource?,
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    // Avoid capturing unstable source in actions lambda
    val title = source?.name
    val isLocalSource = source is LocalAnimeSource
    val isConfigurableSource = source is ConfigurableAnimeSource

    SearchToolbar(
        navigateUp = navigateUp,
        // Aurora: «назад» — круглая стеклянная линза (эталон: детали расширения).
        customNavigationIcon = { AuroraBackLens(onClick = navigateUp) },
        titleContent = { AppBarTitle(title) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        onSearch = onSearch,
        onClickCloseSearch = navigateUp,
        // Поиск живёт в линзах ниже: прежнее поведение (клик открывает поле,
        // «крестик» сбрасывает запрос) сохранено, колбэки те же.
        searchEnabled = false,
        actions = {
            Row(
                modifier = Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchQuery == null) {
                    AnimeToolbarLens(
                        icon = Icons.Outlined.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                        onClick = { onSearchQueryChange("") },
                    )
                } else if (searchQuery.isNotEmpty()) {
                    AnimeToolbarLens(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(MR.strings.action_reset),
                        onClick = { onSearchQueryChange("") },
                    )
                }
                // Линза-цикл режимов отображения: сетка → комфортная сетка → список
                // (порядок прототипа); иконка показывает текущий режим.
                AnimeToolbarLens(
                    icon = when (displayMode) {
                        LibraryDisplayMode.List -> Icons.AutoMirrored.Filled.ViewList
                        LibraryDisplayMode.ComfortableGrid -> Icons.Filled.ViewComfy
                        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid ->
                            Icons.Filled.ViewModule
                    },
                    contentDescription = stringResource(MR.strings.action_display_mode),
                    highlighted = displayMode != LibraryDisplayMode.List,
                    onClick = { onDisplayModeChange(displayMode.nextCatalogMode()) },
                )
                if (!isLocalSource) {
                    AnimeToolbarLens(
                        icon = Icons.Outlined.Public,
                        contentDescription = stringResource(MR.strings.action_open_in_web_view),
                        onClick = onWebViewClick,
                    )
                }
                if (isLocalSource || isConfigurableSource) {
                    var overflowOpen by remember { mutableStateOf(false) }
                    Box {
                        AnimeToolbarLens(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            onClick = { overflowOpen = true },
                        )
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            if (isLocalSource) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.label_help)) },
                                    onClick = {
                                        overflowOpen = false
                                        onHelpClick()
                                    },
                                )
                            }
                            if (isConfigurableSource) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.action_settings)) },
                                    onClick = {
                                        overflowOpen = false
                                        onSettingsClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Цикл режимов каталога из линзы тулбара (прежнее меню выбора заменено циклом). */
private fun LibraryDisplayMode.nextCatalogMode(): LibraryDisplayMode = when (this) {
    LibraryDisplayMode.CompactGrid -> LibraryDisplayMode.ComfortableGrid
    LibraryDisplayMode.ComfortableGrid -> LibraryDisplayMode.List
    LibraryDisplayMode.List -> LibraryDisplayMode.CompactGrid
    LibraryDisplayMode.CoverOnlyGrid -> LibraryDisplayMode.CompactGrid
}

/**
 * Круглая стеклянная линза 40dp действия тулбара — единый стиль верхних кнопок
 * приложения (auroraHeaderIconSurface); [highlighted] — accent-градиент + кромка,
 * как у активных плашек прототипа каталога.
 */
@Composable
private fun AnimeToolbarLens(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                if (highlighted) {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.35f),
                                    lerp(colors.accent, Color.Black, 0.45f).copy(alpha = 0.40f),
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .border(1.dp, chromeAccentDetails().copy(alpha = 0.50f), CircleShape)
                } else {
                    Modifier.auroraHeaderIconSurface(
                        colors = colors,
                        hazeState = LocalHomeHazeState.current,
                    )
                },
            )
            .clickable {
                appHaptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) Color.White else colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

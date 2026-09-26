package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsDefaultAppUiFont
import eu.kanade.tachiyomi.ui.more.MoreEntryId
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val MORE_MENU_HIDDEN_HEADER_KEY = "more-menu-hidden-header"
private const val MORE_MENU_HIDDEN_EMPTY_KEY = "more-menu-hidden-empty"

/**
 * «Настройка меню»: order and visibility of the entries shown on the Aurora «More» screen.
 *
 * The visible rows occupy the lazy indices `0..visible.lastIndex`, which is what makes the drag
 * callback able to clamp its target index instead of letting a row land in the hidden group.
 */
@Composable
fun MoreMenuCustomizeContent(
    visible: List<MoreEntryId>,
    hidden: List<MoreEntryId>,
    movedTabTitle: String,
    movedTabIcon: ImageVector,
    onMove: (from: Int, to: Int) -> Unit,
    onToggleHidden: (MoreEntryId) -> Unit,
    onReset: () -> Unit,
    navigateUp: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val lazyListState = rememberLazyListState()
    val uiStyle = rememberResolvedSettingsUiStyle()
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val darkRimLightEnabled by uiPreferences.auroraDarkRimLightEnabled().collectAsState()

    SettingsScaffold(
        title = stringResource(AYMR.strings.aurora_more_menu_customize_title),
        uiStyle = uiStyle,
        onBackPressed = navigateUp,
        // Top bar is pinned on purpose: the hint above the list does not scroll, so a hiding bar
        // would desynchronize with it; back and «Reset» also stay reachable without scrolling up.
        topBarCanScroll = { false },
        actions = {
            AuroraTopBarIconButton(
                onClick = onReset,
                icon = Icons.Outlined.RestartAlt,
                contentDescription = stringResource(MR.strings.action_reset),
            )
        },
    ) { paddingValues ->
        val visibleState = remember { visible.toMutableStateList() }
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val target = to.index.coerceIn(0, visibleState.lastIndex.coerceAtLeast(0))
            if (from.index in visibleState.indices) {
                val item = visibleState.removeAt(from.index)
                visibleState.add(target, item)
                onMove(from.index, target)
            }
        }

        LaunchedEffect(visible) {
            if (!reorderableState.isAnyItemDragging) {
                visibleState.clear()
                visibleState.addAll(visible)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Text(
                text = stringResource(AYMR.strings.aurora_more_menu_hint),
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.medium + 4.dp,
                    end = MaterialTheme.padding.medium + 4.dp,
                    top = 8.dp,
                    bottom = 10.dp,
                ),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.medium,
                ),
            ) {
                items(
                    items = visibleState,
                    key = { it.name },
                ) { id ->
                    ReorderableItem(reorderableState, key = id.name) {
                        MoreMenuCustomizeItem(
                            modifier = Modifier.animateItem(),
                            entryId = id,
                            isHidden = false,
                            movedTabTitle = movedTabTitle,
                            movedTabIcon = movedTabIcon,
                            darkRimLightEnabled = darkRimLightEnabled,
                            dragHandleModifier = Modifier.draggableHandle(),
                            onToggleHidden = { onToggleHidden(id) },
                        )
                    }
                }

                item(key = MORE_MENU_HIDDEN_HEADER_KEY) {
                    MoreMenuHiddenHeader(count = hidden.size)
                }

                if (hidden.isEmpty()) {
                    item(key = MORE_MENU_HIDDEN_EMPTY_KEY) {
                        Text(
                            text = stringResource(AYMR.strings.aurora_more_menu_hidden_empty),
                            color = colors.textSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(
                        items = hidden,
                        key = { "hidden-${it.name}" },
                    ) { id ->
                        MoreMenuCustomizeItem(
                            modifier = Modifier.animateItem(),
                            entryId = id,
                            isHidden = true,
                            movedTabTitle = movedTabTitle,
                            movedTabIcon = movedTabIcon,
                            darkRimLightEnabled = darkRimLightEnabled,
                            onToggleHidden = { onToggleHidden(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreMenuHiddenHeader(count: Int) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(AYMR.strings.aurora_more_menu_hidden_count, count),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider),
        )
    }
}

@Composable
private fun MoreMenuCustomizeItem(
    entryId: MoreEntryId,
    isHidden: Boolean,
    movedTabTitle: String,
    movedTabIcon: ImageVector,
    darkRimLightEnabled: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val useMediumWeight = LocalIsDefaultAppUiFont.current
    val appHaptics = LocalAppHaptics.current
    val pinned = entryId.isPinned

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AURORA_MORE_CARD_VERTICAL_INSET)
            .auroraCardStyle(colors, RoundedCornerShape(20.dp), applyDarkRimLight = darkRimLightEnabled),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!colors.isDark && !colors.isEInk) {
                Color.Transparent
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isHidden) {
                Spacer(modifier = Modifier.width(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = dragHandleModifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = moreEntryIcon(entryId, movedTabIcon),
                contentDescription = null,
                tint = if (isHidden) colors.textSecondary else colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = MoreEntryTitle(entryId, movedTabTitle),
                    color = if (isHidden) colors.textSecondary else colors.textPrimary,
                    style = auroraPrimaryMenuTitleTextStyle(
                        baseStyle = MaterialTheme.typography.bodyLarge,
                        useMediumWeight = useMediumWeight,
                    ),
                )
                if (pinned) {
                    Text(
                        text = stringResource(AYMR.strings.aurora_more_menu_pinned_summary),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
            if (pinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(18.dp),
                )
            } else {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    IconButton(
                        onClick = {
                            appHaptics.tap()
                            onToggleHidden()
                        },
                    ) {
                        Icon(
                            imageVector = if (isHidden) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (isHidden) {
                                    AYMR.strings.aurora_more_menu_show
                                } else {
                                    AYMR.strings.action_hide
                                },
                            ),
                            tint = if (isHidden) colors.textSecondary else colors.accent,
                        )
                    }
                }
            }
        }
    }
}

package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.anime.components.AnimeSourceIcon
import eu.kanade.presentation.browse.anime.components.BaseAnimeSourceItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourcesScreen(
    state: AnimeSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    searchQuery: String? = null,
    onChangeSearchQuery: ((String) -> Unit)? = null,
    onToggleLanguage: ((String) -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val hasSearchQuery = !searchQuery.isNullOrBlank()
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty && !hasSearchQuery -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            FastScrollLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                // Поиск: всегда раскрытое компактное поле (44dp), как в манга-эталоне.
                if (searchQuery != null && onChangeSearchQuery != null) {
                    item(key = "search") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (colors.isDark) {
                                            Color.White.copy(alpha = 0.05f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            if (colors.isDark) {
                                                listOf(
                                                    Color.White.copy(alpha = 0.20f),
                                                    Color.White.copy(alpha = 0.05f),
                                                )
                                            } else {
                                                listOf(
                                                    Color.Black.copy(alpha = 0.12f),
                                                    Color.Black.copy(alpha = 0.04f),
                                                )
                                            },
                                        ),
                                        shape = CircleShape,
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onChangeSearchQuery,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = colors.textPrimary,
                                    ),
                                    cursorBrush = SolidColor(colors.accent),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    stringResource(MR.strings.action_search),
                                                    color = colors.textSecondary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Filled.Close,
                                        null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onChangeSearchQuery("") },
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.isEmpty) {
                    item(key = "no-results") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyScreen(stringRes = MR.strings.no_results_found)
                        }
                    }
                }

                // Pinned Carousel
                if (state.pinnedItems.isNotEmpty()) {
                    item(key = "pinned-carousel") {
                        Column {
                            Text(
                                text = stringResource(MR.strings.pinned_sources),
                                style = MaterialTheme.typography.header,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.pinnedItems, key = { "pinned-${it.key()}" }) { source ->
                                    PinnedAnimeSourceCard(
                                        modifier = Modifier.animateItem(),
                                        source = source,
                                        onClickItem = onClickItem,
                                        onLongClickItem = onLongClickItem,
                                        onClickPin = onClickPin,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                itemsIndexed(
                    items = state.items,
                    contentType = { _, it ->
                        when (it) {
                            is AnimeSourceUiModel.Header -> "header"
                            is AnimeSourceUiModel.Item -> "item"
                        }
                    },
                    key = { _, it ->
                        when (it) {
                            // BRM-13: stable per language - Header.hashCode() changed with
                            // isCollapsed and destroyed the item on every toggle (manga etalon).
                            is AnimeSourceUiModel.Header -> "header-${it.language}"
                            is AnimeSourceUiModel.Item -> "source-${it.source.key()}"
                        }
                    },
                ) { index, model ->
                    when (model) {
                        is AnimeSourceUiModel.Header -> {
                            AnimeSourceHeader(
                                modifier = Modifier.animateItem(),
                                language = model.language,
                                isCollapsed = model.isCollapsed,
                                // BRM-14: the PINNED group never collapses (SM ignores it).
                                collapsible = model.language != PINNED_KEY,
                                onToggle = { onToggleLanguage?.invoke(model.language) },
                            )
                        }
                        is AnimeSourceUiModel.Item -> {
                            Column {
                                if (state.items.getOrNull(index - 1) is AnimeSourceUiModel.Item) {
                                    ElegantAnimeSourceDivider()
                                }
                                AnimeSourceItem(
                                    modifier = Modifier.animateItem(),
                                    source = model.source,
                                    onClickItem = onClickItem,
                                    onLongClickItem = onLongClickItem,
                                    onClickPin = onClickPin,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSourceHeader(
    language: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = LocaleHelper.getSourceDisplayName(language, context),
            style = MaterialTheme.typography.header,
        )
        if (collapsible) {
            Icon(
                imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnimeSourceItem(
    source: AnimeSource,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    modifier: Modifier = Modifier,
    showLatest: Boolean = true,
) {
    BaseAnimeSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest && showLatest) {
                // Как в манга-эталоне: без обводки — только иконка + текст.
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = chromeAccent(),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.latest),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = chromeAccent(),
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
            // Feed (Reels) sources always live in the fixed REELS group: pinning is a no-op
            // for them, so the affordance is hidden.
            if (!source.isFeedSource) {
                AnimeSourcePinButton(
                    isPinned = Pin.Pinned in source.pin,
                    onClick = { onClickPin(source) },
                )
            }
        },
    )
}

@Composable
private fun AnimeSourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val chrome = chromeAccent()
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        if (isPinned) {
            // Как в манга-эталоне: просто крашеная иконка без круглого фона.
            Icon(
                imageVector = Icons.Filled.PushPin,
                tint = chrome,
                contentDescription = stringResource(description),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                contentDescription = stringResource(description),
            )
        }
    }
}

/**
 * Контрастный «хром»-акцент (эталон манги): смесь accent с textPrimary (30%),
 * чтобы аффордансы оставались читаемыми при любом акценте темы.
 */
@Composable
private fun chromeAccent(): Color {
    val colors = AuroraTheme.colors
    return lerp(colors.accent, colors.textPrimary, 0.30f)
}

/** Светящаяся градиентная линия между источниками (эталон манги). */
@Composable
private fun ElegantAnimeSourceDivider() {
    val chrome = chromeAccent()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            chrome.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** Закреплённый источник — стеклянная карточка с димом по верхней грани (эталон манги). */
@Composable
private fun PinnedAnimeSourceCard(
    source: AnimeSource,
    onClickItem: (AnimeSource, Listing) -> Unit,
    onLongClickItem: (AnimeSource) -> Unit,
    onClickPin: (AnimeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .width(172.dp)
                .combinedClickable(
                    onClick = {
                        onClickItem(
                            source,
                            if (source.supportsLatest) Listing.Latest else Listing.Popular,
                        )
                    },
                    onLongClick = { onLongClickItem(source) },
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        if (colors.isDark) {
                            listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.45f))
                        },
                    ),
                    shape = shape,
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                AnimeSourceIcon(source = source)
            }
            Text(
                text = source.visualName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        // Дим именно по верхней грани: светящаяся кромка 2dp, гаснущая к краям.
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                chromeAccent().copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        // Пин кликабелен (открепить); для feed-источников пин — no-op, скрываем.
        if (!source.isFeedSource) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = stringResource(MR.strings.action_unpin),
                tint = chromeAccent(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clickable { onClickPin(source) },
            )
        }
    }
}

@Composable
fun AnimeSourceOptionsDialog(
    source: AnimeSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                if (!source.isFeedSource) {
                    val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                    Text(
                        text = stringResource(textId),
                        modifier = Modifier
                            .clickable(onClick = onClickPin)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                if (source.id != LocalAnimeSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface AnimeSourceUiModel {
    data class Item(val source: AnimeSource) : AnimeSourceUiModel
    data class Header(val language: String, val isCollapsed: Boolean) : AnimeSourceUiModel
}

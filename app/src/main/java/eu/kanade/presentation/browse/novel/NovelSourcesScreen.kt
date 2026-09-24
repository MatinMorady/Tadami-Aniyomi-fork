package eu.kanade.presentation.browse.novel

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
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.novel.components.BaseNovelSourceItem
import eu.kanade.presentation.browse.novel.components.NovelSourceIcon
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.browse.novel.source.NovelSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Source
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
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun NovelSourcesScreen(
    state: NovelSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
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
                                    PinnedNovelSourceCard(
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
                            is NovelSourceUiModel.Header -> "header"
                            is NovelSourceUiModel.Item -> "item"
                        }
                    },
                    key = { _, it ->
                        when (it) {
                            // BRN5-control/BRM-13: stable per language (manga etalon).
                            is NovelSourceUiModel.Header -> "header-${it.language}"
                            is NovelSourceUiModel.Item -> "source-${it.source.key()}"
                        }
                    },
                ) { index, model ->
                    when (model) {
                        is NovelSourceUiModel.Header -> {
                            SourceHeader(
                                modifier = Modifier.animateItem(),
                                language = model.language,
                                isCollapsed = model.isCollapsed,
                                // BRM-14: the PINNED group never collapses (SM ignores it).
                                collapsible = model.language != PINNED_KEY,
                                onToggle = { onToggleLanguage?.invoke(model.language) },
                            )
                        }
                        is NovelSourceUiModel.Item -> {
                            Column {
                                if (state.items.getOrNull(index - 1) is NovelSourceUiModel.Item) {
                                    ElegantNovelSourceDivider()
                                }
                                SourceItem(
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
private fun SourceHeader(
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
private fun SourceItem(
    source: Source,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
    showLatest: Boolean = true,
) {
    BaseNovelSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        content = { src, sourceLangString ->
            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .weight(1f),
            ) {
                // Name row with JS/KT badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = src.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val isKt = src.isKotlinExtension
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isKt) Color(0xFF7F52FF) else Color(0xFFF0B429),
                                shape = RoundedCornerShape(3.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (isKt) "KT" else "JS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 0.3.sp,
                            ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                if (sourceLangString != null) {
                    Text(
                        modifier = Modifier.secondaryItemAlpha(),
                        text = sourceLangString,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
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
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.latest),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = chromeAccent(),
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
            SourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun SourcePinButton(
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
                modifier = Modifier.size(20.dp),
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
private fun ElegantNovelSourceDivider() {
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
private fun PinnedNovelSourceCard(
    source: Source,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
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
                NovelSourceIcon(source = source)
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
        // Пин кликабелен: открепить прямо с карточки; фон убран — только крашеная иконка.
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

@Composable
fun NovelSourceOptionsDialog(
    source: Source,
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
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                Text(
                    text = stringResource(MR.strings.action_disable),
                    modifier = Modifier
                        .clickable(onClick = onClickDisable)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

package eu.kanade.presentation.browse.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseMangaSourceList(
    mangaList: LazyPagingItems<Manga>,
    favoriteMangaUrls: Set<String>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val sourceListState = rememberLazyListState()
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyColumn(
        modifier = androidx.compose.ui.Modifier.auroraCenteredMaxWidth(auroraAdaptiveSpec.listMaxWidthDp),
        state = sourceListState,
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = mangaList.itemCount,
            key = { index -> mangaBrowseItemKey(mangaList[index]?.url, index) },
        ) { index ->
            val manga = mangaList[index] ?: return@items
            val isFavorite = androidx.compose.runtime.remember(manga.url, favoriteMangaUrls) {
                manga.url in
                    favoriteMangaUrls
            }
            Column {
                // Светящийся разделитель между строками (язык Browse-хаба).
                if (index > 0) {
                    SourceListRowDivider()
                }
                BrowseMangaSourceListRow(
                    manga = manga,
                    isFavorite = isFavorite,
                    onClick = { onMangaClick(manga) },
                    onLongClick = { onMangaLongClick(manga) },
                )
            }
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading ||
                mangaList.loadState.append is LoadState.Loading
            ) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

/**
 * Строка списка каталога по прототипу: обложка 44x62dp (rounded 9), название,
 * мета-строка textSecondary (автор/художник, иначе «в библиотеке» для избранных).
 */
@Composable
private fun BrowseMangaSourceListRow(
    manga: Manga,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val meta = manga.author?.takeIf { it.isNotBlank() }
        ?: manga.artist?.takeIf { it.isNotBlank() }
        ?: if (isFavorite) stringResource(MR.strings.in_library) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ItemCover.Book(
            modifier = Modifier
                .size(width = 44.dp, height = 62.dp)
                .alpha(if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f),
            data = MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = isFavorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            ),
            shape = RoundedCornerShape(9.dp),
            errorPainter = placeholderPainter,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        if (isFavorite) {
            Icon(
                imageVector = Icons.Outlined.CollectionsBookmark,
                contentDescription = stringResource(MR.strings.in_library),
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Светящаяся градиентная линия между строками (двухслойный glow, паттерн ElegantSourceDivider). */
@Composable
private fun SourceListRowDivider() {
    val chrome = chromeAccentDetails()
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
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

package eu.kanade.presentation.browse.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseMangaSourceCompactGrid(
    mangaList: LazyPagingItems<Manga>,
    favoriteMangaUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val auroraAdaptiveSpecSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = androidx.compose.ui.Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpecSpec.updatesMaxWidthDp ?: auroraAdaptiveSpecSpec.entryMaxWidthDp,
        ),
        // Отступы/шаг прототипа каталога: постеры без внутренней рамки, зазор 10dp.
        contentPadding = contentPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (mangaList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            BrowseSourcePosterItem(
                manga = manga,
                isFavorite = isFavorite,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

/**
 * Постер каталога источника по одобренному прототипу (prototype_source_catalog.html):
 * скругление 14dp, hairline-рамка 1dp, нижний scrim-градиент с названием в 2 строки
 * поверх обложки, accent-бейдж «в библиотеке» (top-start). Общий для компактной и
 * комфортной сеток каталога.
 *
 * E-ink: без scrim-градиентов — hairline-рамка и сплошная подпись под постером.
 */
@Composable
internal fun BrowseSourcePosterItem(
    manga: Manga,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val hairline = if (colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val coverData = MangaCover(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = isFavorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
    val coverModifier = Modifier
        .fillMaxWidth()
        .alpha(if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f)

    if (colors.isEInk) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .border(1.dp, hairline, shape),
        ) {
            ItemCover.Book(
                modifier = coverModifier,
                data = coverData,
                shape = shape,
                errorPainter = placeholderPainter,
            )
            Text(
                text = manga.title,
                modifier = Modifier.padding(6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = coverTitleFontFamily,
                color = colors.textPrimary,
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(1.dp, hairline, shape),
    ) {
        ItemCover.Book(
            modifier = coverModifier,
            data = coverData,
            shape = shape,
            errorPainter = placeholderPainter,
        )
        // Scrim под названием: снизу rgba(5,2,4,.92), к 55% — .55, кверху растворяется.
        // Light-тема: scrim инвертируется в белый (подпись textPrimary остаётся читаемой).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = if (colors.isDark) {
                            arrayOf(
                                0f to Color.Transparent,
                                0.45f to Color(0x8C050204),
                                1f to Color(0xEB050204),
                            )
                        } else {
                            arrayOf(
                                0f to Color.Transparent,
                                0.45f to Color(0x8CFFFFFF),
                                1f to Color(0xEBFFFFFF),
                            )
                        },
                    ),
                ),
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp)
                    .padding(bottom = 7.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = coverTitleFontFamily,
                color = colors.textPrimary,
            )
        }
        if (isFavorite) {
            Badge(
                imageVector = Icons.Outlined.CollectionsBookmark,
                color = colors.accent.copy(alpha = 0.85f),
                iconColor = Color.White,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }
}

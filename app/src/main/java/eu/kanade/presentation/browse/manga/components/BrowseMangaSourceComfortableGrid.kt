package eu.kanade.presentation.browse.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseMangaSourceComfortableGrid(
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
            // Постер прототипа каталога: тот же стеклянный постер, что в компактной
            // сетке (комфортный режим отличается шириной колонок — 2 по прототипу).
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

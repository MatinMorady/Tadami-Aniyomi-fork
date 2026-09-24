package eu.kanade.presentation.browse.anime.components

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
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAnimeSourceComfortableGrid(
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
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
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = animeList.itemCount,
            key = { index -> animeBrowseItemKey(animeList[index]?.url, index) },
        ) { index ->
            val anime = animeList[index] ?: return@items
            val isFavorite = androidx.compose.runtime.remember(anime.url, favoriteAnimeUrls) {
                anime.url in
                    favoriteAnimeUrls
            }
            // Постер прототипа каталога: тот же стеклянный постер, что в компактной
            // сетке (комфортный режим отличается шириной колонок — 2 по прототипу).
            AnimeSourcePosterItem(
                anime = anime,
                isFavorite = isFavorite,
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeLongClick(anime) },
            )
        }

        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

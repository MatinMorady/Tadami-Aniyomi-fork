package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * One saved album's content (device feature, sign-off prototype): a 3-column preview grid of
 * the category feed with per-item progress bars (watch history fraction) and watched badges
 * (manual mark or >=90% progress); the search field filters the loaded pages client-side;
 * a tap opens the regular reels feed in niche mode at that clip; a long press offers the
 * manual watched mark toggle.
 */
data class ReelsAlbumScreen(
    val sourceId: Long,
    val albumId: String,
    val albumName: String,
) : Screen {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsAlbumScreenModel(sourceId, albumId, albumName) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var query by remember { mutableStateOf("") }
        var showSearch by remember { mutableStateOf(false) }
        var menuItem by remember { mutableStateOf<ShortVideoItem?>(null) }

        val filtered = remember(state.items, query) {
            if (query.isBlank()) {
                state.items
            } else {
                state.items.filter { it.title.orEmpty().contains(query, ignoreCase = true) }
            }
        }

        val gridState = rememberLazyGridState()
        LaunchedEffect(gridState, state.items.size) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { lastVisible ->
                    if (lastVisible >= state.items.size - 6) screenModel.loadMore()
                }
        }

        Scaffold(
            topBar = {
                Column {
                    AppBar(
                        title = albumName,
                        navigateUp = navigator::pop,
                        actions = {
                            // Report fix: save/unsave this album straight from the AppBar.
                            IconButton(onClick = { screenModel.toggleAlbumSaved() }) {
                                Icon(
                                    imageVector = if (state.isAlbumSaved) {
                                        Icons.Filled.Bookmark
                                    } else {
                                        Icons.Outlined.BookmarkBorder
                                    },
                                    contentDescription = stringResource(MR.strings.reels_albums),
                                    tint = if (state.isAlbumSaved) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            // Report fix: start the album as a reels feed from the first video.
                            IconButton(
                                onClick = {
                                    navigator.push(
                                        ReelsFeedScreen(
                                            sourceId = sourceId,
                                            nicheId = albumId,
                                            nicheName = albumName,
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(MR.strings.reels_album_search),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(MR.strings.reels_album_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            },
        ) { paddingValues ->
            when {
                state.items.isEmpty() && state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.items.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.reels_feed_empty,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val progress = state.progress[item.id] ?: 0f
                        val watched = item.id in state.watched || progress >= 0.9f
                        AlbumItemCard(
                            item = item,
                            progress = progress,
                            watched = watched,
                            onClick = {
                                navigator.push(
                                    ReelsFeedScreen(
                                        sourceId = sourceId,
                                        nicheId = albumId,
                                        nicheName = albumName,
                                        initialVideoId = item.id,
                                        // Report fix: resume the saved watch position of the
                                        // tapped clip inside the album feed.
                                        resumeVideoId = item.id,
                                    ),
                                )
                            },
                            onLongClick = { menuItem = item },
                        )
                    }
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }

        menuItem?.let { item ->
            val watched = item.id in state.watched
            AlertDialog(
                onDismissRequest = { menuItem = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (watched) {
                                screenModel.unmarkWatched(item.id)
                            } else {
                                screenModel.markWatched(item.id)
                            }
                            menuItem = null
                        },
                    ) {
                        Text(
                            stringResource(
                                if (watched) {
                                    MR.strings.reels_album_unmark_watched
                                } else {
                                    MR.strings.reels_album_mark_watched
                                },
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { menuItem = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
                title = {
                    Text(text = item.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AlbumItemCard(
        item: ShortVideoItem,
        progress: Float,
        watched: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (watched) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = stringResource(MR.strings.reels_album_mark_watched),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = item.title.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

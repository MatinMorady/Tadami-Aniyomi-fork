package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * Hidden-content manager (audit H7): lists every local "not interested" decision with its
 * source, allows unhiding one entry or all of them. Reachable from the feed's More menu.
 */
class ReelsHiddenScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ReelsHiddenScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var confirmClearAll by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_hidden_manage),
                    navigateUp = navigator::pop,
                    actions = {
                        if (state.entries.isNotEmpty()) {
                            IconButton(onClick = { confirmClearAll = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = stringResource(MR.strings.reels_hidden_clear_all),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.entries.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.reels_hidden_empty,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                ) {
                    items(
                        items = state.entries,
                        key = { entry -> Triple(entry.sourceId, entry.kind, entry.value) },
                    ) { entry ->
                        HiddenRow(
                            entry = entry,
                            sourceName = state.sourceNames[entry.sourceId],
                            onUnhide = { screenModel.unhide(entry) },
                        )
                    }
                }
            }
        }

        if (confirmClearAll) {
            AlertDialog(
                onDismissRequest = { confirmClearAll = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClearAll = false
                            screenModel.unhideAll()
                        },
                    ) {
                        Text(stringResource(MR.strings.reels_hidden_clear_all))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearAll = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
                title = { Text(stringResource(MR.strings.reels_hidden_clear_all)) },
                text = { Text(stringResource(MR.strings.reels_hidden_clear_confirm)) },
            )
        }
    }
}

@Composable
private fun HiddenRow(
    entry: ReelsHiddenEntry,
    sourceName: String?,
    onUnhide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.kind == ReelsHiddenEntry.KIND_AUTHOR) {
                Icons.Filled.Person
            } else {
                Icons.Filled.Movie
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = entry.label ?: entry.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onUnhide) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(MR.strings.reels_hidden_unhide),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

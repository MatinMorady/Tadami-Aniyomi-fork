package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsUiStyle
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.delay
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun ExtensionStoreCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (url: String, displayName: String) -> Unit,
    repoUrls: ImmutableSet<String>,
) {
    var url by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val urlAlreadyExists = remember(url) { repoUrls.contains(url) }

    fun suggestName(rawUrl: String): String {
        val clean = rawUrl
            .removePrefix("https://").removePrefix("http://")
            .trim('/')
        val segments = clean.split("/")
        return when {
            segments.size >= 4 && segments[0] == "raw.githubusercontent.com" ->
                "${segments[1]}/${segments[2]}"
            segments.size >= 2 -> segments.take(2).joinToString("/")
            else -> clean
        }
    }

    val dialogContent: @Composable () -> Unit = {
        Column {
            Text(
                text = stringResource(MR.strings.repo_add_legal_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = url,
                onValueChange = { newUrl ->
                    url = newUrl
                    if (!nameManuallyEdited) {
                        displayName = suggestName(newUrl)
                    }
                },
                label = { Text(text = stringResource(MR.strings.label_add_store_input)) },
                supportingText = {
                    val msgRes = if (url.isNotEmpty() && urlAlreadyExists) {
                        MR.strings.error_store_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = url.isNotEmpty() && urlAlreadyExists,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    nameManuallyEdited = it.isNotEmpty()
                },
                label = { Text(text = stringResource(MR.strings.label_display_name)) },
                placeholder = {
                    Text(text = suggestName(url).ifEmpty { stringResource(MR.strings.display_name_auto_detected) })
                },
                singleLine = true,
            )
        }
    }

    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraFrostDialog(
            onDismiss = onDismissRequest,
            title = stringResource(MR.strings.action_add_store),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_add),
                    onClick = {
                        val name = displayName.ifBlank { suggestName(url) }
                        onCreate(url, name)
                        onDismissRequest()
                    },
                    enabled = url.isNotEmpty() && !urlAlreadyExists,
                )
            },
        ) {
            dialogContent()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    enabled = url.isNotEmpty() && !urlAlreadyExists,
                    onClick = {
                        val name = displayName.ifBlank { suggestName(url) }
                        onCreate(url, name)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_add_store))
            },
            text = { dialogContent() },
        )
    }

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun ExtensionStoreDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    repo: String,
) {
    val dialogContent: @Composable () -> Unit = {
        Text(text = stringResource(MR.strings.delete_store_confirmation, repo))
    }

    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraFrostDialog(
            onDismiss = onDismissRequest,
            title = stringResource(MR.strings.action_delete_store),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_ok),
                    onClick = {
                        onDelete()
                        onDismissRequest()
                    },
                )
            },
        ) {
            dialogContent()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    onDismissRequest()
                }) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_delete_store))
            },
            text = { dialogContent() },
        )
    }
}

@Composable
fun ExtensionStoreRenameDialog(
    repo: ExtensionRepo,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(repo.name) { mutableStateOf(repo.name) }
    val focusRequester = remember { FocusRequester() }

    val dialogContent: @Composable () -> Unit = {
        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            value = name,
            onValueChange = { name = it },
            label = { Text(text = stringResource(MR.strings.name)) },
            singleLine = true,
        )
    }

    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraFrostDialog(
            onDismiss = onDismissRequest,
            title = stringResource(MR.strings.action_rename_store),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_rename_store),
                    onClick = {
                        onRename(name.trim())
                        onDismissRequest()
                    },
                    enabled = name.isNotBlank(),
                )
            },
        ) {
            dialogContent()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onRename(name.trim())
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_rename_store))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_rename_store))
            },
            text = { dialogContent() },
        )
    }

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun ExtensionStoreConflictDialog(
    oldRepo: ExtensionRepo,
    newRepo: ExtensionRepo,
    onDismissRequest: () -> Unit,
    onMigrate: () -> Unit,
) {
    val dialogContent: @Composable () -> Unit = {
        Text(text = stringResource(MR.strings.action_replace_store_message, newRepo.name, oldRepo.name))
    }

    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraFrostDialog(
            onDismiss = onDismissRequest,
            title = stringResource(MR.strings.action_replace_store_title),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_replace_store),
                    onClick = {
                        onMigrate()
                        onDismissRequest()
                    },
                )
            },
        ) {
            dialogContent()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = {
                        onMigrate()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_replace_store))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_replace_store_title))
            },
            text = { dialogContent() },
        )
    }
}

@Composable
fun ExtensionStoreConfirmDialog(
    onDismissRequest: () -> Unit,
    onCreate: () -> Unit,
    repo: String,
) {
    val dialogContent: @Composable () -> Unit = {
        Text(text = stringResource(MR.strings.add_store_confirmation, repo))
    }

    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
        AuroraFrostDialog(
            onDismiss = onDismissRequest,
            title = stringResource(MR.strings.action_add_store),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_add),
                    onClick = {
                        onCreate()
                        onDismissRequest()
                    },
                )
            },
        ) {
            dialogContent()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.action_add_store))
            },
            text = { dialogContent() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreate()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

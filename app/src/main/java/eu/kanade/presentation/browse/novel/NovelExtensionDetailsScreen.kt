package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.domain.extension.novel.interactor.NovelExtensionSourceItem
import eu.kanade.presentation.browse.components.AuroraBackLens
import eu.kanade.presentation.browse.components.ExtensionAuroraButton
import eu.kanade.presentation.browse.components.ExtensionBannerTone
import eu.kanade.presentation.browse.components.ExtensionDetailsChip
import eu.kanade.presentation.browse.components.ExtensionDetailsDivider
import eu.kanade.presentation.browse.components.ExtensionDetailsGlassCard
import eu.kanade.presentation.browse.components.ExtensionStatusBanner
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.runtime.hasVisiblePluginSettingsByDiscovery
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreenModel
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun NovelExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: NovelExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val repoUrl = remember(state.extension) { state.extension?.repoUrl?.takeIf { it.isNotBlank() } }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                customNavigationIcon = { AuroraBackLens(onClick = navigateUp) },
                actions = {
                    // Верхние кнопки в общем стиле приложения: круглые стеклянные линзы.
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (repoUrl != null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .auroraHeaderIconSurface(
                                        colors = AuroraTheme.colors,
                                        hazeState = LocalHomeHazeState.current,
                                    )
                                    .clickable { uriHandler.openUri(repoUrl) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Launch,
                                    contentDescription = stringResource(MR.strings.action_open_repo),
                                    tint = AuroraTheme.colors.textPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        var overflowOpen by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .auroraHeaderIconSurface(
                                    colors = AuroraTheme.colors,
                                    hazeState = LocalHomeHazeState.current,
                                )
                                .clickable { overflowOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(MR.strings.action_menu),
                                tint = AuroraTheme.colors.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.action_enable_all)) },
                                    onClick = {
                                        overflowOpen = false
                                        onClickEnableAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.action_disable_all)) },
                                    onClick = {
                                        overflowOpen = false
                                        onClickDisableAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.pref_clear_cookies)) },
                                    onClick = {
                                        overflowOpen = false
                                        onClickClearCookies()
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val extension = state.extension
        if (extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        ExtensionDetails(
            contentPadding = paddingValues,
            extension = extension,
            hasUpdate = state.hasUpdate,
            needsReinstall = state.needsReinstall,
            installStep = state.installStep,
            sources = state.sources,
            incognitoMode = state.isIncognito,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickUninstall = onClickUninstall,
            onClickUpdate = onClickUpdate,
            onClickReinstall = onClickReinstall,
            onClickSource = onClickSource,
            onClickIncognito = onClickIncognito,
        )
    }
}

@Composable
private fun ExtensionDetails(
    contentPadding: PaddingValues,
    extension: NovelPlugin.Installed,
    hasUpdate: Boolean,
    needsReinstall: Boolean,
    installStep: InstallStep,
    sources: ImmutableList<NovelExtensionSourceItem>,
    incognitoMode: Boolean,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    var showUninstallConfirm by remember { mutableStateOf(false) }

    ScrollbarLazyColumn(contentPadding = contentPadding) {
        item {
            ExtensionProblemBanners(
                hasUpdate = hasUpdate,
                needsReinstall = needsReinstall,
                installStep = installStep,
                onClickUpdate = onClickUpdate,
                onClickReinstall = onClickReinstall,
            )
        }

        item {
            DetailsHeader(
                extension = extension,
                extIncognitoMode = incognitoMode,
                onClickUninstall = { showUninstallConfirm = true },
                onExtIncognitoChange = onClickIncognito,
                hasUpdate = hasUpdate,
                busy = !installStep.isCompleted(),
                onClickUpdate = onClickUpdate,
                settingsContent = {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.pref_incognito_mode),
                        subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                        icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                        widget = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = incognitoMode,
                                    onCheckedChange = onClickIncognito,
                                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                                )
                            }
                        },
                    )
                    sources.forEach { source ->
                        ExtensionDetailsDivider()
                        SourceSwitchPreference(
                            source = source,
                            onClickSourcePreferences = onClickSourcePreferences,
                            onClickSource = onClickSource,
                        )
                    }
                },
            )
        }
    }
    if (showUninstallConfirm) {
        AuroraFrostDialog(
            onDismiss = { showUninstallConfirm = false },
            title = stringResource(MR.strings.ext_uninstall),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = { showUninstallConfirm = false },
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.ext_uninstall),
                    onClick = {
                        showUninstallConfirm = false
                        onClickUninstall()
                    },
                )
            },
        ) {
            Text(
                text = stringResource(MR.strings.ext_uninstall_confirm, extension.name),
                color = AuroraTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ExtensionProblemBanners(
    hasUpdate: Boolean,
    needsReinstall: Boolean,
    installStep: InstallStep,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
) {
    val busy = !installStep.isCompleted()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        when {
            needsReinstall -> ExtensionStatusBanner(
                icon = Icons.Outlined.Warning,
                title = stringResource(MR.strings.ext_reinstall_required),
                message = stringResource(MR.strings.ext_reinstall_required_hint),
                tone = ExtensionBannerTone.Warning,
                actionLabel = stringResource(
                    if (busy) MR.strings.ext_installing else MR.strings.ext_reinstall_required,
                ),
                actionEnabled = !busy,
                onAction = onClickReinstall,
            )
            // B+: «Обновить» живёт первичной кнопкой внутри hero, баннер только для warning/error.
        }
    }
}

@Composable
private fun DetailsHeader(
    extension: NovelPlugin.Installed,
    extIncognitoMode: Boolean,
    onClickUninstall: () -> Unit,
    onExtIncognitoChange: (Boolean) -> Unit,
    hasUpdate: Boolean = false,
    busy: Boolean = false,
    updateVersion: String? = null,
    onClickUpdate: () -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .padding(top = MaterialTheme.padding.medium, bottom = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val debugInfo = buildString {
                            append("Plugin name: ${extension.name} (lang: ${extension.lang}; id: ${extension.id})\n")
                            append("Version: ${extension.versionName} (${extension.versionCode})\n")
                            append("Site: ${extension.site}\n")
                            append("Store: ${extension.repoUrl}\n")
                            append("Has settings: ${extension.hasSettings}\n")
                        }
                        context.copyToClipboard("Novel plugin debug information", debugInfo)
                    }
                    .padding(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // B+: иконка 72dp с радиальным accent-свечением под ней.
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AuroraTheme.colors.accent.copy(alpha = 0.28f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    AsyncImage(
                        model = extension.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = extension.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                )

                // B+: статы компактными чипами вместо колонок.
                Row(
                    modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtensionDetailsChip(text = extension.versionName)
                    ExtensionDetailsChip(
                        text = LocaleHelper.getSourceDisplayName(extension.lang, context),
                    )
                }

                // B+: первичное действие внутри hero.
                if (hasUpdate) {
                    ExtensionAuroraButton(
                        text = when {
                            busy -> stringResource(MR.strings.ext_installing)
                            updateVersion != null -> stringResource(MR.strings.ext_update_to, updateVersion)
                            else -> stringResource(MR.strings.ext_update)
                        },
                        onClick = onClickUpdate,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.padding.medium),
                    )
                }
            }
        }

        ExtensionAuroraButton(
            text = stringResource(MR.strings.ext_uninstall),
            onClick = onClickUninstall,
            accent = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )

        // B+: настройки и источники — единый стеклянный блок со светящимися разделителями.
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            settingsContent()
        }
    }
}

@Composable
private fun SourceSwitchPreference(
    source: NovelExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val context = LocalContext.current

    TextPreferenceWidget(
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        widget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.source is ConfigurableNovelSource || source.source.hasVisiblePluginSettingsByDiscovery()) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Switch(
                    checked = source.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = { onClickSource(source.source.id) },
    )
}

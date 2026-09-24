package eu.kanade.presentation.browse.anime

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
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
import androidx.compose.material.icons.outlined.ErrorOutline
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
import com.tadami.aurora.R
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.components.ExtensionAuroraButton
import eu.kanade.presentation.browse.components.ExtensionBannerTone
import eu.kanade.presentation.browse.components.ExtensionDetailsChip
import eu.kanade.presentation.browse.components.ExtensionDetailsDivider
import eu.kanade.presentation.browse.components.ExtensionDetailsGlassCard
import eu.kanade.presentation.browse.components.ExtensionStatusBanner
import eu.kanade.presentation.browse.manga.NsfwWarningDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreenModel
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionDetailsScreenModel.State,
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
    val url = remember(state.extension) {
        val regex = """https://raw.githubusercontent.com/(.+?)/(.+?)/.+""".toRegex()
        regex.find(state.extension?.repoUrl.orEmpty())
            ?.let {
                val (user, repo) = it.destructured
                "https://github.com/$user/$repo"
            }
            ?: state.extension?.repoUrl
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    // Верхние кнопки в общем стиле приложения: круглые стеклянные линзы.
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (url != null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .auroraHeaderIconSurface(
                                        colors = AuroraTheme.colors,
                                        hazeState = LocalHomeHazeState.current,
                                    )
                                    .clickable { uriHandler.openUri(url) },
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
        if (state.extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        AnimeExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
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
private fun AnimeExtensionDetails(
    contentPadding: PaddingValues,
    extension: AnimeExtension.Installed,
    installStep: InstallStep,
    sources: ImmutableList<AnimeExtensionSourceItem>,
    incognitoMode: Boolean,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showNsfwWarning by remember { mutableStateOf(false) }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            ExtensionProblemBanners(
                extension = extension,
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
                onClickAppInfo = {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", extension.pkgName, null)
                        context.startActivity(this)
                    }
                    Unit
                }.takeIf { extension.isShared },
                onClickAgeRating = {
                    showNsfwWarning = true
                },
                onExtIncognitoChange = onClickIncognito,
                hasUpdate = extension.hasUpdate,
                busy = !installStep.isCompleted(),
                updateVersion = extension.updateVersion,
                onClickUpdate = onClickUpdate,
                settingsContent = {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.pref_incognito_mode),
                        subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                        icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                        widget = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
    if (showNsfwWarning) {
        NsfwWarningDialog(
            onClickConfirm = {
                showNsfwWarning = false
            },
        )
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
    extension: AnimeExtension.Installed,
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
        if (extension.isObsolete) {
            ExtensionStatusBanner(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(MR.strings.ext_obsolete),
                message = stringResource(MR.strings.obsolete_extension_message),
                tone = ExtensionBannerTone.Error,
            )
        }
        when {
            extension.needsReinstall -> ExtensionStatusBanner(
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
    extension: AnimeExtension,
    extIncognitoMode: Boolean,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickAppInfo: (() -> Unit)?,
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
            .padding(
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val extDebugInfo = buildString {
                            append(
                                """
                                Extension name: ${extension.name} (lang: ${extension.lang}; package: ${extension.pkgName})
                                Extension version: ${extension.versionName} (lib: ${extension.libVersion}; version code: ${extension.versionCode})
                                NSFW: ${extension.isNsfw}
                                """.trimIndent(),
                            )

                            if (extension is AnimeExtension.Installed) {
                                append("\n\n")
                                append(
                                    """
                                    Update available: ${extension.hasUpdate}
                                    Orphaned: ${extension.isObsolete}
                                    Shared: ${extension.isShared}
                                    Store: ${extension.repoUrl}
                                    """.trimIndent(),
                                )
                            }
                        }
                        context.copyToClipboard("Extension Debug information", extDebugInfo)
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
                    AnimeExtensionIcon(
                        modifier = Modifier
                            .size(72.dp),
                        extension = extension,
                        density = DisplayMetrics.DENSITY_XXXHIGH,
                    )
                }

                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                val strippedPkgName = extension.pkgName.substringAfter(
                    "eu.kanade.tachiyomi.animeextension.",
                )

                Text(
                    text = strippedPkgName,
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
                    if (extension.isNsfw) {
                        ExtensionDetailsChip(
                            text = stringResource(MR.strings.ext_nsfw_short),
                            warn = true,
                            onClick = onClickAgeRating,
                        )
                    }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ExtensionAuroraButton(
                text = stringResource(MR.strings.ext_uninstall),
                onClick = onClickUninstall,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )

            if (onClickAppInfo != null) {
                ExtensionAuroraButton(
                    text = stringResource(MR.strings.ext_app_info),
                    onClick = onClickAppInfo,
                    accent = AuroraTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // B+: настройки и источники — единый стеклянный блок со светящимися разделителями.
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            settingsContent()
        }
    }
}

@Composable
private fun SourceSwitchPreference(
    source: AnimeExtensionSourceItem,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (source.source is ConfigurableAnimeSource) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                            tint = AuroraTheme.colors.textSecondary,
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

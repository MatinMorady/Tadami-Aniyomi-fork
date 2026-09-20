package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.ui.home.HomeHubSection
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryRow(
    history: List<HomeHubHistory>,
    recentCardMode: HomeHubRecentCardMode,
    section: HomeHubSection,
    highlightedEntryId: Long? = null,
    onEntryClick: (Long) -> Unit,
    onViewAllClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val sectionHorizontalPadding = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 24.dp
        AuroraDeviceClass.TabletCompact -> 28.dp
        AuroraDeviceClass.TabletExpanded -> 32.dp
    }
    val cardWidth = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 128.dp
        AuroraDeviceClass.TabletCompact -> 152.dp
        AuroraDeviceClass.TabletExpanded -> 176.dp
    }
    val rowSpacing = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 14.dp
        AuroraDeviceClass.TabletCompact -> 16.dp
        AuroraDeviceClass.TabletExpanded -> 18.dp
    }
    val useWrappedSections = shouldUseHomeHubWrappedSections(auroraAdaptiveSpec.deviceClass)
    val cardRenderMode = remember(recentCardMode) {
        resolveHomeHubRecentCardRenderMode(recentCardMode)
    }
    val progressLabelRes = remember(section) {
        when (section) {
            HomeHubSection.Anime -> AYMR.strings.aurora_episode_number
            HomeHubSection.Manga, HomeHubSection.Novel -> AYMR.strings.aurora_chapter_number
        }
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(
                stringResource(AYMR.strings.aurora_recently_watched),
                color = colors.textPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 18.sp,
            )
            androidx.compose.material3.Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onViewAllClick()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        if (useWrappedSections) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = sectionHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                history.forEach { item ->
                    HomeHubRecentCard(
                        mode = cardRenderMode,
                        modifier = Modifier.width(cardWidth),
                        title = item.title,
                        coverData = item.coverData,
                        subtitle = stringResource(
                            progressLabelRes,
                            formatProgressNumber(section, item.progressNumber),
                        ),
                        onClick = { onEntryClick(item.entryId) },
                        deviceClass = auroraAdaptiveSpec.deviceClass,
                        highlighted = item.entryId == highlightedEntryId,
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp),
                contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                items(
                    items = history,
                    key = { it.entryId },
                    contentType = { "home_hub_history_card" },
                ) { item ->
                    HomeHubRecentCard(
                        mode = cardRenderMode,
                        modifier = Modifier.width(cardWidth),
                        title = item.title,
                        coverData = item.coverData,
                        subtitle = stringResource(
                            progressLabelRes,
                            formatProgressNumber(section, item.progressNumber),
                        ),
                        onClick = { onEntryClick(item.entryId) },
                        deviceClass = auroraAdaptiveSpec.deviceClass,
                        highlighted = item.entryId == highlightedEntryId,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecommendationsGrid(
    recommendations: List<HomeHubRecommendation>,
    section: HomeHubSection,
    recentCardMode: HomeHubRecentCardMode,
    onEntryClick: (Long) -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val sectionHorizontalPadding = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 24.dp
        AuroraDeviceClass.TabletCompact -> 28.dp
        AuroraDeviceClass.TabletExpanded -> 32.dp
    }
    val cardWidth = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 128.dp
        AuroraDeviceClass.TabletCompact -> 152.dp
        AuroraDeviceClass.TabletExpanded -> 176.dp
    }
    val rowSpacing = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 14.dp
        AuroraDeviceClass.TabletCompact -> 16.dp
        AuroraDeviceClass.TabletExpanded -> 18.dp
    }
    val useWrappedSections = shouldUseHomeHubWrappedSections(auroraAdaptiveSpec.deviceClass)
    val cardRenderMode = remember(recentCardMode) {
        resolveHomeHubRecentCardRenderMode(recentCardMode)
    }
    val recommendationFormat = remember(section) {
        when (section) {
            HomeHubSection.Anime -> AYMR.strings.aurora_episode_progress_format
            HomeHubSection.Manga, HomeHubSection.Novel -> AYMR.strings.aurora_chapter_progress_format
        }
    }

    Column(modifier = Modifier.padding(top = 32.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(
                stringResource(AYMR.strings.aurora_recently_added),
                color = colors.textPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 18.sp,
            )
            androidx.compose.material3.Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        if (useWrappedSections) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = sectionHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                recommendations.forEach { item ->
                    val recommendationSubtitle = if (item.subtitle != null) {
                        item.subtitle
                    } else {
                        stringResource(
                            recommendationFormat,
                            item.progressNumerator.toInt(),
                            item.progressDenominator.toInt(),
                        )
                    }
                    HomeHubRecentCard(
                        mode = cardRenderMode,
                        modifier = Modifier.width(cardWidth),
                        title = item.title,
                        coverData = item.coverData,
                        subtitle = recommendationSubtitle,
                        onClick = { onEntryClick(item.entryId) },
                        deviceClass = auroraAdaptiveSpec.deviceClass,
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp),
                contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                items(
                    items = recommendations,
                    key = { it.entryId },
                    contentType = { "home_hub_recommendation_card" },
                ) { item ->
                    val recommendationSubtitle = if (item.subtitle != null) {
                        item.subtitle
                    } else {
                        stringResource(
                            recommendationFormat,
                            item.progressNumerator.toInt(),
                            item.progressDenominator.toInt(),
                        )
                    }
                    HomeHubRecentCard(
                        mode = cardRenderMode,
                        modifier = Modifier.width(cardWidth),
                        title = item.title,
                        coverData = item.coverData,
                        subtitle = recommendationSubtitle,
                        onClick = { onEntryClick(item.entryId) },
                        deviceClass = auroraAdaptiveSpec.deviceClass,
                    )
                }
            }
        }
    }
}

internal fun formatProgressNumber(section: HomeHubSection, number: Double): String {
    return when (section) {
        HomeHubSection.Anime -> formatEpisodeNumber(number)
        HomeHubSection.Manga, HomeHubSection.Novel -> formatChapterNumber(number)
    }
}

/**
 * В режимах Collage/Stage hero-слот занят лентой «Для тебя», а последний прочитанный
 * тайтл исключён из истории screen-моделью (чтобы не дублировать hero). Возвращаем
 * его первым элементом ряда — лимит ряда сохранён, хвост сдвигается.
 */
internal fun prependLastReadHero(
    hero: HomeHubHero?,
    history: List<HomeHubHistory>,
    heroPresentation: HomeHeroMode,
    section: HomeHubSection,
    limit: Int = 6,
): List<HomeHubHistory> {
    if (hero == null) return history
    if (heroPresentation != HomeHeroMode.Collage && heroPresentation != HomeHeroMode.Stage) return history
    val item = HomeHubHistory(
        entryId = hero.entryId,
        title = hero.title,
        progressNumber = hero.progressNumber,
        coverData = hero.coverData,
        section = section,
    )
    return (listOf(item) + history.filterNot { it.entryId == hero.entryId }).take(limit)
}

/** id карточки для акцентного кольца — только когда hero-слот занят discovery. */
internal fun lastReadHighlightId(hero: HomeHubHero?, heroPresentation: HomeHeroMode): Long? =
    if (hero != null && (heroPresentation == HomeHeroMode.Collage || heroPresentation == HomeHeroMode.Stage)) {
        hero.entryId
    } else {
        null
    }

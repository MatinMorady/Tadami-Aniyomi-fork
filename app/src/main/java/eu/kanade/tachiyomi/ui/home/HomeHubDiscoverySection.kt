package eu.kanade.tachiyomi.ui.home

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.AuroraSheetWindowFx
import eu.kanade.presentation.components.StageFocusGlowShader
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.components.shouldAnimateAuroraBackground
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.discovery.rrfScores
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.discovery.discoveryCoverData
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.floor

// ============================ Чистые функции (тестируются) ============================

/** Тизер = топ-N смешанного потока (интерлив квот сигналов), а не только LIKE-ряд. */
internal fun composeTeaserItems(
    items: List<DiscoverySuggestion>,
    limit: Int,
    offset: Int = 0,
): List<HomeHubDiscoveryItem> {
    val capped = limit.coerceIn(3, 20)
    val rows = items.groupBy { it.rowType }
        .mapValues { (_, row) ->
            row.map { s ->
                DiscoveryRowItem(s.title, s.cleanTitle, s.coverUrl, s.reason, s.seedTitle, s.provider, s.score)
            }
        }
    val fullMix = interleaveMix(rows, total = items.size, rrf = rrfScores(rows))
    val safeOffset = if (fullMix.isNotEmpty()) offset % fullMix.size else 0
    val rotated = if (safeOffset <= 0) {
        fullMix.take(capped)
    } else {
        (fullMix.drop(safeOffset) + fullMix.take(safeOffset)).take(capped)
    }
    return rotated.mapNotNull { row ->
        items.firstOrNull { it.cleanTitle == row.cleanTitle }?.toHomeHubDiscoveryItem()
    }
}

/**
 * Выбирает элементы тизера с соблюдением 48-часовой уникальности:
 * 1. Исключает тайтлы из [shownTitles] (показанные за последние 48 ч).
 * 2. Если свежих тайтлов >= count, формирует сбалансированный тизер только из свежих.
 * 3. Если свежих тайтлов < count, добирает недостающие из ранее показанных строго в порядке
 *    [shownCutoffMap] (наименее недавно показанные первыми, без искажения квотами рядов).
 * 4. Если весь пул меньше или равен count, циклически ротирует порядок отображения по [offset],
 *    чтобы кнопка обновления и повторный вход не зависали.
 */
internal fun selectFreshTeaserItems(
    pool: List<DiscoverySuggestion>,
    shownTitles: Set<String>,
    count: Int,
    offset: Int = 0,
    shownCutoffMap: Map<String, Long> = emptyMap(),
): List<HomeHubDiscoveryItem> {
    val capped = count.coerceIn(3, 20)
    if (pool.isEmpty()) return emptyList()

    val freshPool = pool.filterNot { it.cleanTitle in shownTitles }
    val shownPool = pool.filter { it.cleanTitle in shownTitles }

    val rawSelection: List<HomeHubDiscoveryItem> = when {
        freshPool.size >= capped -> {
            composeTeaserItems(freshPool, capped, offset = 0)
        }
        freshPool.isNotEmpty() -> {
            val freshItems = freshPool.map { it.toHomeHubDiscoveryItem() }
            val needed = capped - freshItems.size
            val sortedShown = shownPool.sortedBy { shownCutoffMap[it.cleanTitle] ?: 0L }
            val backfillItems = sortedShown.take(needed).map { it.toHomeHubDiscoveryItem() }
            (freshItems + backfillItems).take(capped)
        }
        else -> {
            val sortedShown = shownPool.sortedBy { shownCutoffMap[it.cleanTitle] ?: 0L }
            sortedShown.take(capped).map { it.toHomeHubDiscoveryItem() }
        }
    }

    return if (pool.size <= capped && rawSelection.isNotEmpty()) {
        val safeOffset = offset % rawSelection.size
        if (safeOffset <= 0) {
            rawSelection
        } else {
            rawSelection.drop(safeOffset) + rawSelection.take(safeOffset)
        }
    } else {
        rawSelection
    }
}

/** Секция видна всегда при включённом discovery: при пустой ленте рендерит карточку-вход на полный экран. */
internal fun shouldShowForYouSection(enabled: Boolean): Boolean = enabled

/**
 * B2: первый тег для «скрыть всё с тегом X» — только TASTE reason-CSV
 * (жанры прочих рядов не персистятся; у них пункт меню disabled).
 */
internal fun firstBlacklistTag(
    rowType: DiscoveryRowType,
    reasonPayload: String?,
): String? = when (rowType) {
    DiscoveryRowType.TASTE -> reasonPayload?.splitToSequence(",")
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
    else -> null
}

/** Сколько карточек текущего тизера скроется при блэклисте [tag] (для undo-snackbar). */
internal fun countAffectedTeasers(items: List<HomeHubDiscoveryItem>, tag: String): Int {
    val expanded = eu.kanade.tachiyomi.data.discovery.expandGenreSet(listOf(tag))
    return items.count { item ->
        item.rowType == DiscoveryRowType.TASTE &&
            item.reasonPayload?.splitToSequence(",")?.any { it.trim().lowercase() in expanded } == true
    }
}

/**
 * Локализованная подпись-обоснование. Шаблоны строк передаются параметрами,
 * чтобы функция оставалась чистой (тестируемой без Compose).
 */
internal fun discoveryReasonText(
    item: HomeHubDiscoveryItem,
    similarTemplate: String,
    trendTemplate: String,
    nextSeasonTemplate: String,
): String? = when (item.rowType) {
    DiscoveryRowType.LIKE -> item.seedTitle?.let { similarTemplate.replace("%1\$s", it) }
    DiscoveryRowType.TREND -> when (item.reasonPayload) {
        "next" -> nextSeasonTemplate
        // Тайтлы из каталога источника (novel-first path и fallback) — честно подписываем источником.
        "source" -> item.provider
        else -> trendTemplate
    }
    DiscoveryRowType.TASTE -> item.reasonPayload?.takeIf { it.isNotBlank() }
    DiscoveryRowType.SOURCE -> item.provider
}

/**
 * Режим hero с деградацией: Collage/Hybrid требуют включённый discovery с непустой лентой.
 */
internal fun resolveHeroPresentation(
    prefMode: HomeHeroMode,
    discoveryEnabled: Boolean,
    discoveryCount: Int,
): HomeHeroMode = when (prefMode) {
    HomeHeroMode.Continue -> HomeHeroMode.Continue
    HomeHeroMode.Collage ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Collage else HomeHeroMode.Continue
    HomeHeroMode.Hybrid ->
        if (discoveryEnabled && discoveryCount > 0) HomeHeroMode.Hybrid else HomeHeroMode.Continue
    HomeHeroMode.Stage ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Stage else HomeHeroMode.Continue
}

internal fun DiscoverySuggestion.toHomeHubDiscoveryItem() = HomeHubDiscoveryItem(
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    seedTitle = seedTitle,
    reasonPayload = reason,
    provider = provider,
    rowType = rowType,
    mediaType = mediaType,
)

internal fun HomeHubDiscoveryItem.toSuggestionItem(): SuggestionItem = SuggestionItem(
    title = title,
    searchQueries = listOf(title),
    thumbnailUrl = coverUrl,
    providerName = provider,
    providerUrl = "",
    providerId = null,
    mediaType = when (mediaType) {
        DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
        DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
        DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
    },
    reason = when (provider.lowercase()) {
        "anilist" -> SuggestionReason.EXTERNAL_ANILIST
        "myanimelist", "mal" -> SuggestionReason.EXTERNAL_MAL
        "mangaupdates" -> SuggestionReason.EXTERNAL_MU
        "novelupdates" -> SuggestionReason.EXTERNAL_NU
        "shikimori" -> SuggestionReason.EXTERNAL_SHIKIMORI
        else -> SuggestionReason.SEARCH_TITLE
    },
)

internal fun HomeHubDiscoveryItem.toDiscoverySuggestion(): DiscoverySuggestion = DiscoverySuggestion(
    id = 0L,
    mediaType = mediaType,
    rowType = rowType,
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    reason = reasonPayload,
    seedTitle = seedTitle,
    provider = provider,
    score = 1.0,
    position = 0L,
    createdAt = 0L,
)

// ============================ UI ============================

/** Маппинг секции Home Hub в медиатип discovery (для резолва источника обложки). */
internal fun HomeHubSection.toDiscoveryMediaType(): DiscoveryMediaType = when (this) {
    HomeHubSection.Anime -> DiscoveryMediaType.ANIME
    HomeHubSection.Manga -> DiscoveryMediaType.MANGA
    HomeHubSection.Novel -> DiscoveryMediaType.NOVEL
}

internal data class HybridDiscoveryStripLayoutSpec(
    val cardWidth: Int,
    val sectionHorizontalPadding: Int,
    val rowSpacing: Int,
)

internal fun resolveHybridDiscoveryStripLayoutSpec(deviceClass: AuroraDeviceClass): HybridDiscoveryStripLayoutSpec {
    return when (deviceClass) {
        AuroraDeviceClass.Phone -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 128,
            sectionHorizontalPadding = 24,
            rowSpacing = 14,
        )
        AuroraDeviceClass.TabletCompact -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 152,
            sectionHorizontalPadding = 28,
            rowSpacing = 16,
        )
        AuroraDeviceClass.TabletExpanded -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 176,
            sectionHorizontalPadding = 32,
            rowSpacing = 18,
        )
    }
}

/** Карточка discovery: гармонизирована с HomeHubRecentPosterCard (постер 0.9, скругление 16dp/18dp, текст под постером). */
@Composable
internal fun DiscoveryPosterCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deviceClass: AuroraDeviceClass = AuroraDeviceClass.Phone,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val posterSpec = remember(deviceClass) {
        resolveHomeHubRecentPosterCardSpec(deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        variant = AuroraCoverPlaceholderVariant.Portrait,
    )
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterSpec.posterAspectRatio)
                    .clip(posterShape)
                    .background(posterSurface)
                    .then(
                        if (colors.isDark || colors.isEInk) {
                            Modifier.border(
                                width = 1.dp,
                                color = if (colors.isDark) {
                                    Color.White.copy(alpha = 0.06f)
                                } else {
                                    Color.Black.copy(alpha = 0.04f)
                                },
                                shape = posterShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val posterContext = LocalContext.current
                val posterCoverReloadTick = rememberCoverReloadTick()
                val posterCoverRequest = remember(
                    posterContext,
                    coverUrl,
                    coverMediaType,
                    coverProvider,
                    posterCoverReloadTick,
                ) {
                    buildAuroraCoverImageRequest(
                        posterContext,
                        discoveryCoverData(coverMediaType, coverProvider, coverUrl),
                    )
                }
                AsyncImage(
                    model = posterCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberAuroraPosterColorFilter(),
                    modifier = Modifier.fillMaxSize(),
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
            }
            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = posterSpec.textBlockMinHeightDp.dp)
                    .padding(horizontal = posterSpec.textHorizontalPaddingDp.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = posterSpec.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
            onLongClick = {
                appHaptics.tap()
                onLongClick()
            },
        )
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
        )
    }

    if (isLightTheme) {
        Box(
            modifier = modifier
                .drawBehind {
                    val radius = 18.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .then(clickModifier),
        ) {
            cardContent()
        }
    } else {
        Box(
            modifier = modifier
                .clip(cardShape)
                .background(outerSurface)
                .then(
                    if (colors.isDark || colors.isEInk) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (colors.isDark) {
                                Color.White.copy(alpha = 0.06f)
                            } else {
                                Color.Black.copy(alpha = 0.05f)
                            },
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(clickModifier),
        ) {
            cardContent()
        }
    }
}

@Composable
private fun discoveryReasonOrNull(item: HomeHubDiscoveryItem): String? {
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val showReasons by discoveryPreferences.showReasons().collectAsStateWithLifecycle()
    if (!showReasons) return null
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    return discoveryReasonText(item, similarTemplate, trendTemplate, nextTemplate)
}

/** Круглая кнопка обновления ленты (общая для заголовка ForYouSection и Hybrid-полосы). */
@Composable
private fun DiscoveryRefreshIconButton(isRefreshing: Boolean, onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val rotationAnim = rememberInfiniteTransition(label = "discovery_refresh_rot")
    val rotationAngle by if (isRefreshing) {
        rotationAnim.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "refresh_angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val refreshRimBrush = remember(colors) {
        if (colors.isEInk) {
            SolidColor(colors.divider)
        } else {
            Brush.verticalGradient(
                listOf(
                    if (colors.isDark) {
                        Color.White.copy(
                            alpha = 0.18f,
                        )
                    } else {
                        Color.White.copy(alpha = 0.50f)
                    },
                    Color.Transparent,
                ),
            )
        }
    }
    val refreshTintBrush = remember(colors) {
        if (colors.isEInk) {
            SolidColor(Color.Transparent)
        } else {
            Brush.verticalGradient(
                listOf(
                    if (colors.isDark) {
                        Color.White.copy(
                            alpha = 0.08f,
                        )
                    } else {
                        Color.White.copy(alpha = 0.20f)
                    },
                    Color.Transparent,
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(
                1.dp,
                refreshRimBrush,
                CircleShape,
            )
            .background(
                brush = refreshTintBrush,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = {
                    appHaptics.tap()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = stringResource(AYMR.strings.for_you_refresh),
            tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
        )
    }
}

/** Тизер-секция «Для тебя» на Home Hub: заголовок + «Ещё» + горизонтальный рельс карточек. */
@Composable
internal fun ForYouSection(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    if (items.isEmpty()) {
        EmptyForYouCard(onMoreClick = onMoreClick)
        return
    }
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

    Column(modifier = Modifier.padding(top = 32.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AYMR.strings.aurora_for_you),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                if (onRefreshClick != null) {
                    Spacer(Modifier.width(10.dp))
                    DiscoveryRefreshIconButton(isRefreshing = isRefreshing, onClick = onRefreshClick)
                }
            }
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "home_hub_discovery_card" },
            ) { item ->
                DiscoveryPosterCard(
                    modifier = Modifier.width(cardWidth),
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    deviceClass = auroraAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
                    onLongClick = onLongClick?.let { { it(item) } },
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                )
            }
        }
    }
}

/** Полоса из 3 плиток под compact-hero в гибридном режиме (гармонизирована с HomeHubRecentPosterCard). */
@Composable
internal fun HybridDiscoveryStrip(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val stripAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val stripMaxWidthDp = stripAdaptiveSpec.updatesMaxWidthDp ?: stripAdaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(stripAdaptiveSpec.deviceClass)
    }
    val cardWidth = layoutSpec.cardWidth.dp
    val sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp
    val rowSpacing = layoutSpec.rowSpacing.dp
    val posterSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHomeHubRecentPosterCardSpec(stripAdaptiveSpec.deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(stripMaxWidthDp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AYMR.strings.aurora_for_you),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onRefreshClick != null) {
                    Spacer(Modifier.width(10.dp))
                    DiscoveryRefreshIconButton(isRefreshing = isRefreshing, onClick = onRefreshClick)
                }
            }
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "hybrid_discovery_tile" },
            ) { item ->
                DiscoveryPosterCard(
                    modifier = Modifier.width(cardWidth),
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    deviceClass = stripAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                    onLongClick = onLongClick?.let { { it(item) } },
                )
            }
            if (items.isNotEmpty()) {
                item(key = "hybrid_discovery_more", contentType = "hybrid_discovery_more") {
                    val moreContent: @Composable () -> Unit = {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(posterSpec.posterAspectRatio)
                                    .clip(posterShape)
                                    .background(posterSurface)
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                colors.accent.copy(alpha = 0.40f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        posterShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(AYMR.strings.for_you_all_picks),
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
                            Spacer(Modifier.height(posterSpec.textBlockMinHeightDp.dp))
                        }
                    }

                    if (isLightTheme) {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .drawBehind {
                                    val radius = 18.dp.toPx()
                                    val cornerRadius = CornerRadius(radius, radius)
                                    val neutralOffsetY = 3.dp.toPx()
                                    val warmOffsetY = 5.dp.toPx()
                                    val neutralInset = 1.dp.toPx()
                                    val warmInset = 3.dp.toPx()

                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.035f),
                                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                    drawRoundRect(
                                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                                        size = Size(width = size.width - warmInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                }
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.78f),
                                            Color.White.copy(alpha = 0.68f),
                                            Color.White.copy(alpha = 0.60f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.75f),
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.12f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .clip(cardShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .clip(cardShape)
                                .background(outerSurface)
                                .then(
                                    if (colors.isDark || colors.isEInk) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = if (colors.isDark) {
                                                Color.White.copy(alpha = 0.06f)
                                            } else {
                                                Color.Black.copy(alpha = 0.05f)
                                            },
                                            shape = cardShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero «Коллаж»: мозаика 440dp из топ-5 смешанного потока
 * (доминантная плитка + 4 малых), реролл = ротация кэша без сети.
 */
@Composable
internal fun DiscoveryHeroCollage(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val intervalHours by discoveryPreferences.collageRotationIntervalHours().collectAsStateWithLifecycle()
    val animSpeed by discoveryPreferences.collageAnimationSpeed().collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(discoveryPreferences.collageOffset().get()) }
    var userInteractionToken by remember { mutableIntStateOf(0) }

    val updateOffset: (Int) -> Unit = { newOffset ->
        offset = newOffset
        discoveryPreferences.collageOffset().set(newOffset)
    }

    // Авто-ротация с настраиваемым интервалом (от 1 до 24 ч, 0 = отключено)
    if (!colors.isEInk && items.size > 5 && intervalHours > 0) {
        LaunchedEffect(items.size, intervalHours, userInteractionToken) {
            val intervalMillis = intervalHours * 3600_000L
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastTime = discoveryPreferences.collageLastRotationTime().get()
                val elapsed = now - lastTime
                if (lastTime == 0L) {
                    discoveryPreferences.collageLastRotationTime().set(now)
                    delay(intervalMillis)
                } else if (elapsed >= intervalMillis) {
                    updateOffset(offset + 1)
                    discoveryPreferences.collageLastRotationTime().set(now)
                    delay(intervalMillis)
                } else {
                    val remaining = maxOf(1000L, intervalMillis - elapsed)
                    delay(remaining)
                    updateOffset(offset + 1)
                    discoveryPreferences.collageLastRotationTime().set(System.currentTimeMillis())
                }
            }
        }
    }

    val staggerStep = when (animSpeed) {
        "fast" -> 40
        "smooth" -> 110
        else -> 70
    }

    // Окно до 5 плиток через seeded-shuffle: реролл (offset+1) всегда меняет порядок/состав
    // при любом размере ленты (фикс «мёртвого реролла» и дублей при <5 айтемов).
    val tiles = remember(items, offset) {
        items.shuffled(kotlin.random.Random(offset)).take(5)
    }
    val rest = tiles.drop(1)
    val col1 = listOfNotNull(rest.getOrNull(0), rest.getOrNull(2))
    val col2 = listOfNotNull(rest.getOrNull(1), rest.getOrNull(3))
    val outerShape = RoundedCornerShape(20.dp)
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp

    Box(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, outerShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp)) {
            AnimatedContent(
                targetState = tiles[0],
                transitionSpec = {
                    resolveCollageSlotTransition(
                        delayMillis = 0,
                        isEInk = colors.isEInk,
                        speed = animSpeed,
                    )
                },
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
                label = "collage_hero_slot",
            ) { targetHero ->
                CollageTile(
                    item = targetHero,
                    big = true,
                    modifier = Modifier.fillMaxSize(),
                    coverMediaType = coverMediaType,
                    coverProvider = targetHero.provider,
                    onClick = { onItemClick(targetHero) },
                    onLongClick = onLongClick?.let { { it(targetHero) } },
                )
            }
            if (col1.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col1.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = staggerStep + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col1_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
            if (col2.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col2.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = (staggerStep * 3) + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col2_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
        }

        // Кнопка обновления в правом верхнем углу: высокий контраст на любых фонах
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (colors.isEInk) {
                        colors.cardBackground
                    } else if (colors.isDark) {
                        Color.Black.copy(alpha = 0.75f)
                    } else {
                        Color.White.copy(alpha = 0.90f)
                    },
                )
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.22f),
                    ),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = {
                        appHaptics.tap()
                        discoveryPreferences.collageLastRotationTime().set(System.currentTimeMillis())
                        userInteractionToken++
                        updateOffset(offset + 1)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        // Кнопка перехода в стиле Aurora Hero CTA («Продолжить / Читать»)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val buttonInteractionSource = remember { MutableInteractionSource() }
            AuroraGlassCtaSurface(
                mode = AuroraHeroCtaMode.Aurora,
                onClick = {
                    appHaptics.tap()
                    onMoreClick()
                },
                modifier = Modifier.height(44.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_all_picks),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

internal fun resolveCollageSlotTransition(
    delayMillis: Int,
    isEInk: Boolean,
    speed: String = "normal",
): ContentTransform {
    return if (isEInk) {
        fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
    } else {
        val (enterDuration, exitDuration) = when (speed) {
            "fast" -> 250 to 200
            "smooth" -> 700 to 500
            else -> 420 to 300
        }
        val enter = fadeIn(animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis),
            )
        val exit = fadeOut(animationSpec = tween(durationMillis = exitDuration)) +
            scaleOut(targetScale = 1.02f, animationSpec = tween(durationMillis = exitDuration))
        enter togetherWith exit
    }
}

// ==================== Hero «Кинематографичный фокус» (stage) ====================

/** Сколько слотов держим по каждую сторону от фокуса: 5 видимых (−2..+2) + 2 буфера (±3). */
private const val STAGE_BUFFER = 3

/** Поза слота карусели: только числа — держим её чистой и тестируемой. */
@androidx.compose.runtime.Immutable
internal data class StageSlotPose(
    val scale: Float,
    val alpha: Float,
    val dimAlpha: Float,
    val translationXPercent: Float,
    val rotationYDeg: Float,
)

/**
 * Поза по расстоянию до фокуса: фокус → соседи (±1) → дальние (±2) → невидимый буфер (|rel| ≥ 3).
 * Затемнение выражено [StageSlotPose.dimAlpha], потому что brightness в graphicsLayer недоступен.
 */
internal fun resolveStageSlotPose(rel: Int): StageSlotPose {
    val sign = if (rel < 0) -1f else 1f
    return when (abs(rel)) {
        0 -> StageSlotPose(scale = 1f, alpha = 1f, dimAlpha = 0f, translationXPercent = 0f, rotationYDeg = 0f)
        1 -> StageSlotPose(
            scale = 0.82f,
            alpha = 1f,
            dimAlpha = 0.38f,
            translationXPercent = 42f * sign,
            rotationYDeg = -15f * sign,
        )
        2 -> StageSlotPose(
            scale = 0.7f,
            alpha = 0.72f,
            dimAlpha = 0.6f,
            translationXPercent = 76f * sign,
            rotationYDeg = -24f * sign,
        )
        else -> StageSlotPose(
            scale = 0.62f,
            alpha = 0f,
            dimAlpha = 0.7f,
            translationXPercent = 104f * sign,
            rotationYDeg = -28f * sign,
        )
    }
}

/** Линейная интерполяция позы: карусель движется плавно между целыми позициями. */
internal fun lerpStageSlotPose(from: StageSlotPose, to: StageSlotPose, fraction: Float): StageSlotPose {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * f
    return StageSlotPose(
        scale = mix(from.scale, to.scale),
        alpha = mix(from.alpha, to.alpha),
        dimAlpha = mix(from.dimAlpha, to.dimAlpha),
        translationXPercent = mix(from.translationXPercent, to.translationXPercent),
        rotationYDeg = mix(from.rotationYDeg, to.rotationYDeg),
    )
}

/** Данные слота берутся по модулю: у ленты нет ни начала, ни конца. */
internal fun stageItemIndex(center: Int, slot: Int, size: Int): Int {
    if (size <= 0) return 0
    return ((center + slot) % size + size) % size
}

/** Длительности перехода карусели: e-ink и выключенные системные анимации дают мгновенную смену кадра. */
internal data class StageMotionSpec(val settleMillis: Int, val fadeMillis: Int)

internal fun resolveStageMotionSpec(speed: String, isEInk: Boolean, animationsEnabled: Boolean): StageMotionSpec {
    if (isEInk || !animationsEnabled) return StageMotionSpec(settleMillis = 0, fadeMillis = 0)
    return when (speed) {
        "fast" -> StageMotionSpec(settleMillis = 250, fadeMillis = 200)
        "smooth" -> StageMotionSpec(settleMillis = 700, fadeMillis = 500)
        else -> StageMotionSpec(settleMillis = 420, fadeMillis = 300)
    }
}

/** Авто-ротация: та же политика, что у фоновых анимаций Aurora (e-ink, lifecycle, системные анимации). */
internal fun shouldAutoRotateStage(
    isEInk: Boolean,
    intervalHours: Int,
    isLifecycleResumed: Boolean,
    systemAnimationsEnabled: Boolean,
): Boolean = shouldAnimateAuroraBackground(
    userEnabled = !isEInk && intervalHours > 0,
    isLifecycleResumed = isLifecycleResumed,
    systemAnimationsEnabled = systemAnimationsEnabled,
)

/** Свечение доступно только на API 33+ (RuntimeShader) и бессмысленно в e-ink. */
internal fun shouldUseStageGlowShader(sdkInt: Int, isEInk: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !isEInk

/**
 * Hero «Кинематографичный фокус»: бесконечная карусель подборки — один постер в фокусе,
 * соседи уходят в перспективу. Слоты живут в окне ±3 от непрерывного центра, данные берутся
 * по модулю, поэтому листание идёт по кругу в обе стороны.
 */
@Composable
internal fun DiscoveryHeroStage(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val intervalHours by discoveryPreferences.stageRotationIntervalHours().collectAsStateWithLifecycle()
    val speed by discoveryPreferences.stageAnimationSpeed().collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(discoveryPreferences.stageOffset().get()) }
    var userInteractionToken by remember { mutableIntStateOf(0) }
    var center by rememberSaveable { mutableIntStateOf(0) }

    val ordered = remember(items, offset) { items.shuffled(kotlin.random.Random(offset)) }
    val systemAnimationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val motionSpec = remember(speed, colors.isEInk, systemAnimationsEnabled) {
        resolveStageMotionSpec(speed = speed, isEInk = colors.isEInk, animationsEnabled = systemAnimationsEnabled)
    }
    val animatedCenter = animateFloatAsState(
        targetValue = center.toFloat(),
        // Позднее чтение: значение используется только внутри graphicsLayer, рекомпозиции на кадр нет.
        animationSpec = tween(durationMillis = motionSpec.settleMillis),
        label = "stage_center",
    )
    // Ken-burns у фокуса: состояние читается внутри graphicsLayer, поэтому кадры не рекомпозируют слоты.
    val kenBurnsTransition = rememberInfiniteTransition(label = "stage_ken_burns")
    val kenBurnsScale = kenBurnsTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stage_ken_burns_scale",
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val outerShape = RoundedCornerShape(20.dp)

    // Авто-ротация по кругу: выключается в e-ink, при нулевом интервале, на паузе и без системных анимаций.
    if (ordered.size > 1) {
        if (shouldAutoRotateStage(colors.isEInk, intervalHours, isLifecycleResumed, systemAnimationsEnabled)) {
            LaunchedEffect(ordered.size, intervalHours, userInteractionToken) {
                val intervalMillis = intervalHours * 3600_000L
                while (isActive) {
                    val last = discoveryPreferences.stageLastRotationTime().get()
                    val elapsed = if (last == 0L) 0L else System.currentTimeMillis() - last
                    delay(if (last == 0L) intervalMillis else (intervalMillis - elapsed).coerceAtLeast(1000L))
                    discoveryPreferences.stageLastRotationTime().set(System.currentTimeMillis())
                    center += 1
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, outerShape)
                } else {
                    Modifier
                },
            ),
    ) {
        // Акцентное свечение под фокус-постером: отдельный слой под карточками (не RenderEffect на сцене).
        if (!colors.isEInk) {
            val glowIntensity = if (colors.isDark) 1f else 0.5f
            val glowModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.74f)
                .height(170.dp)
            val glowShader = remember {
                if (shouldUseStageGlowShader(Build.VERSION.SDK_INT, isEInk = false)) {
                    StageFocusGlowShader()
                } else {
                    null
                }
            }
            if (glowShader?.isAvailable == true) {
                Canvas(glowModifier) {
                    with(glowShader) { drawStageGlow(accent = colors.accent, intensity = glowIntensity) }
                }
            } else {
                Box(
                    glowModifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.28f * glowIntensity),
                                Color.Transparent,
                            ),
                        ),
                    ),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 10.dp)
                .pointerInput(ordered.size) {
                    // Свайп коммитится только на onDragEnd: ключ не зависит от center и не рвёт жест.
                    var dragAccumulator = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!change.isConsumed) {
                                dragAccumulator += dragAmount
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (abs(dragAccumulator) > 45.dp.toPx()) {
                                appHaptics.tap()
                                center += if (dragAccumulator < 0) 1 else -1
                                userInteractionToken++
                            }
                            dragAccumulator = 0f
                        },
                    )
                },
        ) {
            for (absIndex in (center - STAGE_BUFFER)..(center + STAGE_BUFFER)) {
                val item = ordered[stageItemIndex(absIndex, 0, ordered.size)]
                val rel = absIndex - center
                key(absIndex) {
                    StageSlot(
                        item = item,
                        rel = rel,
                        absIndex = absIndex,
                        distanceToFocus = abs(rel),
                        isFocus = rel == 0,
                        animatedCenter = animatedCenter,
                        kenBurnsScale = kenBurnsScale,
                        useKenBurns = !colors.isEInk,
                        coverMediaType = coverMediaType,
                        onClick = {
                            appHaptics.tap()
                            if (rel == 0) {
                                onItemClick(item)
                            } else {
                                // Шаг всегда один: слоты живут окном вокруг центра, переброс не нужен.
                                center += if (rel > 0) 1 else -1
                                userInteractionToken++
                            }
                        },
                        onLongClick = onLongClick?.let { callback -> { callback(item) } },
                    )
                }
            }
        }

        // Счётчик позиции: у ленты нет конца, поэтому показываем место в подборке.
        val position = stageItemIndex(center, 0, ordered.size) + 1
        Text(
            text = "${position.toString().padStart(2, '0')} / ${ordered.size.toString().padStart(2, '0')}",
            color = if (colors.isDark && !colors.isEInk) Color.White else colors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (colors.isEInk) colors.cardBackground else Color.Black.copy(alpha = 0.5f))
                .border(1.dp, colors.divider, RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )

        // Стрелки: у ленты нет конца, поэтому обе кнопки всегда активны.
        StageNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(AYMR.strings.for_you_stage_prev),
            onClick = {
                appHaptics.tap()
                center -= 1
                userInteractionToken++
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        )
        StageNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(AYMR.strings.for_you_stage_next),
            onClick = {
                appHaptics.tap()
                center += 1
                userInteractionToken++
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )

        // Реролл: новый порядок подборки, позиция сохраняется (без «проезда» через всю ленту).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (colors.isEInk) {
                        colors.cardBackground
                    } else if (colors.isDark) {
                        Color.Black.copy(alpha = 0.75f)
                    } else {
                        Color.White.copy(alpha = 0.90f)
                    },
                )
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.22f),
                    ),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = {
                        appHaptics.tap()
                        offset += 1
                        discoveryPreferences.stageOffset().set(offset)
                        discoveryPreferences.stageLastRotationTime().set(System.currentTimeMillis())
                        userInteractionToken++
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val buttonInteractionSource = remember { MutableInteractionSource() }
            AuroraGlassCtaSurface(
                mode = AuroraHeroCtaMode.Aurora,
                onClick = {
                    appHaptics.tap()
                    onMoreClick()
                },
                modifier = Modifier.height(44.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_all_picks),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (colors.isEInk) {
                    colors.cardBackground
                } else if (colors.isDark) {
                    Color.Black.copy(alpha = 0.55f)
                } else {
                    Color.White.copy(alpha = 0.85f)
                },
            )
            .border(
                1.dp,
                if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.20f),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (colors.isDark && !colors.isEInk) Color.White else colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Слот карусели. Обложка создаётся только для слотов внутри окна (|rel| ≤ 2), буферные слоты
 * остаются пустыми — так 7 загрузок Coil не плодятся на каждый переброс.
 */
@Composable
private fun BoxScope.StageSlot(
    item: HomeHubDiscoveryItem,
    rel: Int,
    absIndex: Int,
    distanceToFocus: Int,
    isFocus: Boolean,
    animatedCenter: State<Float>,
    kenBurnsScale: State<Float>,
    useKenBurns: Boolean,
    coverMediaType: DiscoveryMediaType,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverMediaType, item.provider, coverReloadTick) {
        buildAuroraCoverImageRequest(context, discoveryCoverData(coverMediaType, item.provider, item.coverUrl))
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(18.dp)
    val focusReason = if (isFocus) discoveryReasonOrNull(item) else null

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxHeight(0.92f)
            .fillMaxWidth(0.6f)
            .zIndex(10f - distanceToFocus)
            .graphicsLayer {
                val relFloat = absIndex - animatedCenter.value
                val base = floor(relFloat).toInt()
                val pose = lerpStageSlotPose(
                    from = resolveStageSlotPose(base),
                    to = resolveStageSlotPose(base + 1),
                    fraction = relFloat - base,
                )
                translationX = size.width * pose.translationXPercent / 100f
                scaleX = pose.scale
                scaleY = pose.scale
                rotationY = pose.rotationYDeg
                cameraDistance = 12f * density
                alpha = pose.alpha
            }
            .then(
                if (isFocus) {
                    Modifier.semantics {
                        contentDescription = listOfNotNull(item.title, focusReason).joinToString(", ")
                    }
                } else {
                    Modifier
                },
            )
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, tileShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        if (distanceToFocus <= 2) {
            // Нейтральная подложка на время загрузки: тематическая Aurora-заглушка слишком яркая для hero-слота.
            val neutralCoverBrush = remember(colors) {
                Brush.verticalGradient(
                    listOf(
                        colors.cardBackground,
                        colors.divider.copy(alpha = if (colors.isEInk) 0.30f else 0.22f),
                    ),
                )
            }
            Box(Modifier.fillMaxSize().background(neutralCoverBrush))
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                // Ken-burns живёт только на обложке: раньше он масштабировал весь слот вместе с подписью.
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val kenBurns = if (useKenBurns && isFocus) kenBurnsScale.value else 1f
                        scaleX = kenBurns
                        scaleY = kenBurns
                    },
                error = fallbackPainter,
                fallback = fallbackPainter,
            )
            // Затемнение соседей: brightness в graphicsLayer нет, поэтому кладём scrim-слой.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val relFloat = absIndex - animatedCenter.value
                        val base = floor(relFloat).toInt()
                        alpha = lerpStageSlotPose(
                            from = resolveStageSlotPose(base),
                            to = resolveStageSlotPose(base + 1),
                            fraction = relFloat - base,
                        ).dimAlpha
                    }
                    .background(if (colors.isEInk) Color.White else Color.Black),
            )
            if (isFocus) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                        ),
                    ),
                )
            }
        }

        if (isFocus) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 70.dp)) {
                Text(
                    item.title,
                    color = if (colors.isEInk) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                discoveryReasonOrNull(item)?.let { reason ->
                    Text(
                        reason,
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageTile(
    item: HomeHubDiscoveryItem,
    big: Boolean,
    modifier: Modifier = Modifier,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverMediaType, coverProvider, coverReloadTick) {
        buildAuroraCoverImageRequest(context, discoveryCoverData(coverMediaType, coverProvider, item.coverUrl))
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, tileShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {
                            appHaptics.tap()
                            onClick()
                        },
                        onLongClick = {
                            appHaptics.tap()
                            onLongClick()
                        },
                    )
                } else {
                    Modifier.clickable {
                        appHaptics.tap()
                        onClick()
                    }
                },
            ),
    ) {
        AsyncImage(
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        // Скрим только на большой плитке и только в нижней трети под заголовком:
        // постеры остаются яркими, как в обычных карточках, а малые плитки без
        // текста не затемняются вовсе.
        if (big) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                    ),
                ),
            )
        }
        if (big) {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    item.title,
                    color = if (colors.isEInk) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                discoveryReasonOrNull(item)?.let { reason ->
                    Text(
                        reason,
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Пустая лента: карточка-вход на полный экран «Для тебя» (там есть рефреш).
 * Без неё при пустом кэше рефреш недостижим (тизер скрывался вместе с точкой входа).
 */
@Composable
private fun EmptyForYouCard(onMoreClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val emptyAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val emptyMaxWidthDp = emptyAdaptiveSpec.updatesMaxWidthDp ?: emptyAdaptiveSpec.entryMaxWidthDp
    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(emptyMaxWidthDp)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (colors.isDark) colors.glass.copy(alpha = 0.10f) else colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_title),
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_subtitle),
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent)
                .clickable {
                    appHaptics.tap()
                    onMoreClick()
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.textOnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** B2: long-press меню — скрыть тайтл или скрыть все подборки с тегом (TASTE). */
@Composable
internal fun DiscoveryHideOptionsSheet(
    itemTitle: String,
    tag: String?,
    onHide: () -> Unit,
    onBlacklistTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val tagEnabled = !tag.isNullOrBlank()
    val supportsBlurBehind = rememberSupportsBlurBehind(colors.isEInk)
    val sheetContainer = when {
        colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
        !supportsBlurBehind -> colors.surface
        colors.isDark -> Color.Black.copy(alpha = 0.70f)
        else -> Color.White.copy(alpha = 0.88f)
    }
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    AdaptiveSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainer,
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        applyStatusBarsPadding = false,
        onRevealChange = { sheetReveal = it },
    ) {
        AuroraSheetWindowFx(sheetReveal)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.15f),
                    ),
            )
            Text(
                itemTitle,
                color = colors.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            )
            Box(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, colors.accent.copy(alpha = 0.22f), Color.Transparent),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            appHaptics.tap()
                            onHide()
                        }
                        .padding(vertical = 13.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_tag_hide_title),
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = tagEnabled) {
                            appHaptics.tap()
                            tag?.let { onBlacklistTag(it) }
                        }
                        .padding(vertical = 13.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.LabelOff,
                        contentDescription = null,
                        tint = if (tagEnabled) colors.accent else colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (tagEnabled) {
                                stringResource(AYMR.strings.for_you_tag_blacklist_action, tag)
                            } else {
                                stringResource(AYMR.strings.for_you_tag_blacklist_action_generic)
                            },
                            color = if (tagEnabled) colors.textPrimary else colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!tagEnabled) {
                            Text(
                                stringResource(AYMR.strings.for_you_tag_blacklist_disabled),
                                color = colors.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

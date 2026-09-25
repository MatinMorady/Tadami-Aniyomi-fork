package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * Ряд «Источник»: popular-витрина выбранного источника расширения
 * (catalogue API источника), опционально с жанровым фильтром (best-effort:
 * только если источник exposes группу жанровых чекбоксов).
 *
 * Жанровый фильтр считается эвристикой [GenreMatcher]: названия чекбоксов
 * источника любого языка/формы сопоставляются с запрошенными жанрами;
 * непонятые названия один раз переводятся через [GenreTranslationFallback].
 *
 * V4: статус выпуска ([DiscoveryReleaseStatus]) прокидывается тем же трактом —
 * эвристика [SourceStatusFilterMatcher] находит статус-фильтр источника (Select/
 * Group/TriState любого языка) и выставляет его в FilterList search-запроса.
 * Одиночный Select при 2+ выбранных статусах не выразим — инжект пропускается.
 * V4.1: в [latest] при выбранном статусе приоритет у search-выдачи со статус-фильтром
 * (getLatestUpdates фильтры не принимает, статус в списках обычно UNKNOWN); источник
 * не выражает статус — прежний пост-фильтр по status айтема (UNKNOWN не режем).
 */
interface DiscoverySourceCatalog {
    suspend fun popular(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>

    suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>

    suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
        releaseStatuses: Set<DiscoveryReleaseStatus> = emptySet(),
    ): List<DiscoveryRowItem>
}

class AppDiscoverySourceCatalog : DiscoverySourceCatalog {

    /** M2: перевод непонятых чекбоксов жанров — ленивый, кэш внутри фолбэка. */
    private val genreFallback by lazy {
        GenreTranslationFallback(translate = { name -> MultilingualQueryHelper.translate(name) })
    }

    override suspend fun popular(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> =
        fetch(mediaType, sourceId, genres = null, releaseStatuses = releaseStatuses)

    override suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = fetch(mediaType, sourceId, genres = genres, releaseStatuses = releaseStatuses)

    override suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = try {
        // V4.1: при выбранном статусе latest-лента источника не выражает фильтр
        // (getLatestUpdates не принимает FilterList, а статус в списках обычно UNKNOWN).
        // Если источник умеет статус своим фильтром — ряд строится из search-выдачи
        // с этим фильтром; иначе — прежний best-effort пост-фильтр по status айтема.
        if (releaseStatuses.isNotEmpty()) {
            statusSearch(mediaType, sourceId, page, releaseStatuses)?.let { return it }
        }
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularManga(page) }
                } else {
                    source.getPopularManga(page)
                }
                val keptMangas = pageData.mangas
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptMangas.size}/${pageData.mangas.size} statuses=$releaseStatuses"
                    }
                }
                keptMangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url)
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularAnime(page) }
                } else {
                    source.getPopularAnime(page)
                }
                val keptAnimes = pageData.animes
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptAnimes.size}/${pageData.animes.size} statuses=$releaseStatuses"
                    }
                }
                keptAnimes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url)
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularNovels(page) }
                } else {
                    source.getPopularNovels(page)
                }
                val keptNovels = pageData.novels
                    .filter { SourceStatusFilterMatcher.entryPasses(it.status, releaseStatuses) }
                if (releaseStatuses.isNotEmpty()) {
                    logcat {
                        "[DiscoverySourceCatalog] latest postfilter source=$sourceId " +
                            "kept=${keptNovels.size}/${pageData.novels.size} statuses=$releaseStatuses"
                    }
                }
                keptNovels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // Тяжёлый источник (напр. Hitomi с in-memory индексом всех галерей) исчерпал heap:
        // Error не ловится catch(Exception) и без этого-guard убивает весь процесс.
        // Ряд выживает без источника, буферы становятся мусором после выхода из catch.
        logcat { "[DiscoverySourceCatalog] latest OOM source=$sourceId" }
        emptyList()
    } catch (e: Exception) {
        logcat { "[DiscoverySourceCatalog] latest FAILED source=$sourceId: ${e.message}" }
        emptyList()
    }

    /**
     * V4.1: search-выдача источника с выставленным статус-фильтром; null — источник
     * не выражает выбранные статусы своим фильтром (одиночный Select при 2+ статусах,
     * отсутствие фильтра) — вызывающий возвращается к пост-фильтру latest.
     */
    private suspend fun statusSearch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem>? = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyMangaStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                source.getSearchManga(page, "", filters).mangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url)
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyAnimeStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                source.getSearchAnime(page, "", filters).animes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url)
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return null
                val filters = source.getFilterList()
                if (!applyNovelStatusFilter(filters, releaseStatuses)) return null
                logcat { "[DiscoverySourceCatalog] statusSearch hit source=$sourceId statuses=$releaseStatuses" }
                source.getSearchNovels(page, "", filters).novels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat { "[DiscoverySourceCatalog] statusSearch FAILED source=$sourceId: ${e.message}" }
        null
    }

    private suspend fun fetch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>?,
        releaseStatuses: Set<DiscoveryReleaseStatus>,
    ): List<DiscoveryRowItem> = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withMangaGenreFilters(source.getFilterList(), genres) else null
                // Жанровый запрос без применимого фильтра — честный пустой результат,
                // а не popular-выдача под видом «твоего вкуса».
                if (genres != null && filters == null) return emptyList()
                // V4: статус выпуска — в тот же FilterList поверх жанров; не применим
                // (нет фильтра / одиночный Select при 2+ статусах) — best-effort без него.
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyMangaStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val page = if (filters == null) source.getPopularManga(1) else source.getSearchManga(1, "", filters)
                page.mangas.mapIndexed { idx, m ->
                    rowItem(m.title, m.thumbnail_url, source.name, idx, sourceId, m.url)
                }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withAnimeGenreFilters(source.getFilterList(), genres) else null
                if (genres != null && filters == null) return emptyList()
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyAnimeStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val page = if (filters == null) source.getPopularAnime(1) else source.getSearchAnime(1, "", filters)
                page.animes.mapIndexed { idx, a ->
                    rowItem(a.title, a.thumbnail_url, source.name, idx, sourceId, a.url)
                }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                var filters = if (genres != null) withNovelGenreFilters(source.getFilterList(), genres) else null
                if (genres != null && filters == null) return emptyList()
                if (releaseStatuses.isNotEmpty()) {
                    val candidate = filters ?: source.getFilterList()
                    val applied = applyNovelStatusFilter(candidate, releaseStatuses)
                    if (applied) filters = candidate
                    logcat {
                        "[DiscoverySourceCatalog] search statusApplied=$applied source=$sourceId statuses=$releaseStatuses"
                    }
                }
                val page = if (filters == null) source.getPopularNovels(1) else source.getSearchNovels(1, "", filters)
                page.novels.mapIndexed { idx, n ->
                    rowItem(n.title, n.thumbnail_url, source.name, idx, sourceId, n.url)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // Не даём Error убить генератор ленты: ряд честно помечается failed через IOException.
        logcat { "[DiscoverySourceCatalog] OOM source=$sourceId" }
        throw IOException("source $sourceId OOM", e)
    } catch (e: Exception) {
        // Пробрасываем: ряд SOURCE помечается failed, кэш не затирается.
        logcat { "[DiscoverySourceCatalog] FAILED source=$sourceId: ${e.message}" }
        throw e
    }

    private fun rowItem(
        title: String,
        thumbnailUrl: String?,
        sourceName: String,
        index: Int,
        sourceId: Long,
        sourceUrl: String?,
    ) = DiscoveryRowItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = thumbnailUrl,
        reason = null,
        seedTitle = null,
        provider = sourceName,
        score = 1.0 - index * 0.01,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
    )

    private suspend fun withMangaGenreFilters(filters: FilterList, genres: List<String>): FilterList? {
        val group = filters.filterIsInstance<Filter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<Filter.CheckBox>()
        // Эвристика жанров: чекбоксы любого языка матчатся с запросом; непонятые
        // названия источник-специфичной экзотики переводятся один раз (M2).
        val matched = genreFallback.selectSourceGenres(genres, boxes.map { it.name })
        boxes.filter { it.name in matched }.forEach { it.state = true }
        return if (matched.isNotEmpty()) filters else null
    }

    private suspend fun withAnimeGenreFilters(filters: AnimeFilterList, genres: List<String>): AnimeFilterList? {
        val group = filters.filterIsInstance<AnimeFilter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<AnimeFilter.CheckBox>()
        val matched = genreFallback.selectSourceGenres(genres, boxes.map { it.name })
        boxes.filter { it.name in matched }.forEach { it.state = true }
        return if (matched.isNotEmpty()) filters else null
    }

    private suspend fun withNovelGenreFilters(filters: NovelFilterList, genres: List<String>): NovelFilterList? {
        val group = filters.filterIsInstance<NovelFilter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<NovelFilter.CheckBox>()
        val matched = genreFallback.selectSourceGenres(genres, boxes.map { it.name })
        boxes.filter { it.name in matched }.forEach { it.state = true }
        return if (matched.isNotEmpty()) filters else null
    }

    /**
     * V4: выставить статус-фильтр источника под выбранные статусы выпуска.
     * Select — одиночный (применим только при одном статусе), Group — чекбоксы/
     * tri-state (любое число), топ-левел TriState/CheckBox — по имени опции.
     * true = фильтр выставлен, запрос пойдёт через search с этим FilterList.
     */
    private fun applyMangaStatusFilter(filters: FilterList, selected: Set<DiscoveryReleaseStatus>): Boolean {
        val select = filters.filterIsInstance<Filter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<Filter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<Filter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<Filter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = Filter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<Filter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = Filter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<Filter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    private fun applyAnimeStatusFilter(
        filters: AnimeFilterList,
        selected: Set<DiscoveryReleaseStatus>,
    ): Boolean {
        val select = filters.filterIsInstance<AnimeFilter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<AnimeFilter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<AnimeFilter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<AnimeFilter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = AnimeFilter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<AnimeFilter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = AnimeFilter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<AnimeFilter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    private fun applyNovelStatusFilter(
        filters: NovelFilterList,
        selected: Set<DiscoveryReleaseStatus>,
    ): Boolean {
        val select = filters.filterIsInstance<NovelFilter.Select<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (select != null) {
            val idx = SourceStatusFilterMatcher.matchSelectIndex(select.values.map(::optionName), selected)
                ?: return false
            select.state = idx
            return true
        }
        val group = filters.filterIsInstance<NovelFilter.Group<*>>()
            .firstOrNull { SourceStatusFilterMatcher.isStatusFilterName(it.name) }
        if (group != null) {
            val boxes = group.state.filterIsInstance<NovelFilter.CheckBox>()
            val matchedBoxes = SourceStatusFilterMatcher.matchOptionNames(boxes.map { it.name }, selected)
            if (boxes.isNotEmpty() && matchedBoxes.isNotEmpty()) {
                boxes.filter { it.name in matchedBoxes }.forEach { it.state = true }
                return true
            }
            val tris = group.state.filterIsInstance<NovelFilter.TriState>()
            val matchedTris = SourceStatusFilterMatcher.matchOptionNames(tris.map { it.name }, selected)
            if (tris.isNotEmpty() && matchedTris.isNotEmpty()) {
                tris.filter { it.name in matchedTris }.forEach { it.state = NovelFilter.TriState.STATE_INCLUDE }
                return true
            }
            return false
        }
        val loneTri = filters.filterIsInstance<NovelFilter.TriState>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneTri != null) {
            loneTri.state = NovelFilter.TriState.STATE_INCLUDE
            return true
        }
        val loneBox = filters.filterIsInstance<NovelFilter.CheckBox>()
            .firstOrNull { SourceStatusFilterMatcher.statusOfOption(it.name) in selected }
        if (loneBox != null) {
            loneBox.state = true
            return true
        }
        return false
    }

    /** Имя опции Select: строка, пара «имя→query» или toString произвольного значения. */
    private fun optionName(value: Any?): String = when (value) {
        is String -> value
        is Pair<*, *> -> value.first?.toString().orEmpty()
        else -> value?.toString().orEmpty()
    }
}

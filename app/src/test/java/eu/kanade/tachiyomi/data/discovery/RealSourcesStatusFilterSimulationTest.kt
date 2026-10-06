package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

/**
 * Живая симуляция статус-фильтра на РЕАЛЬНЫХ формах расширений из extensions-source
 * (src/en + lib-multisrc): пользователь выбрал «только Завершённые» — проверяем,
 * что [AppDiscoverySourceCatalog.applyMangaStatusFilter] находит и выставляет
 * статус-фильтр каждого источника.
 *
 * Формы скопированы 1:1 из кода расширений (имена фильтров и опций):
 * - weebcentral:  UriMultiSelectFilter → Group<CheckBox> «Series Status»
 * - asurascans:   UriPartFilter → Select «Status» (All/Ongoing/Completed/Hiatus/Dropped/Axed)
 * - comickfan:    UriPartFilter → Select «Status» (All/Ongoing/Completed/Cancelled/Hiatus)
 * - mangakakalot: фильтров нет вообще
 * - flamecomics:  фильтров нет вообще
 * - readmanga:    TriStateGroup → Group<TriState> «Статус выхода» (данные grouple)
 * - mangalib:     Group<CheckBox> «Статус тайтла» (данные LibGroup API)
 */
class RealSourcesStatusFilterSimulationTest {

    private val finishedOnly = setOf(DiscoveryReleaseStatus.FINISHED)
    private val catalog = AppDiscoverySourceCatalog()

    // ── EN #1: WeebCentral — Group<CheckBox> «Series Status» ────────────────────

    @Test
    fun `weebcentral series status checkboxes get Complete checked for finished`() = runTest {
        // Filters.kt:136-146 — UriMultiSelectFilter("Series Status", "included_status", …)
        val options = listOf("Ongoing", "Complete", "Hiatus", "Canceled")
        val boxes = options.map { TestCheckBox(it) }
        val group = object : Filter.Group<TestCheckBox>("Series Status", boxes) {}
        val filters = FilterList(group)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe true
        boxes.map { it.name to it.state } shouldBe listOf(
            "Ongoing" to false,
            "Complete" to true,
            "Hiatus" to false,
            "Canceled" to false,
        )
    }

    // ── EN #2: AsuraScans — Select «Status» ─────────────────────────────────────

    @Test
    fun `asurascans select gets Completed index for finished`() = runTest {
        // Filters.kt:27-39 — UriPartFilter("Status", …): All/Ongoing/Completed/Hiatus/Dropped/Axed
        val options = arrayOf("All", "Ongoing", "Completed", "Hiatus", "Dropped", "Axed")
        val select = object : Filter.Select<String>(
            "Status",
            options,
            0,
        ) {}
        val filters = FilterList(select)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe true
        // Индекс «Completed» — сервер Asura получит status=completed.
        select.state shouldBe 2
    }

    // ── EN #3: Comick — Select «Status» (All/Ongoing/Completed/Cancelled/Hiatus) ─

    @Test
    fun `comick select gets Completed index for finished`() = runTest {
        // ComicKFanFilters.kt:114-122 — UriPartFilter("Status", …)
        val options = arrayOf("All", "Ongoing", "Completed", "Cancelled", "Hiatus")
        val select = object : Filter.Select<String>("Status", options, 0) {}
        val filters = FilterList(select)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe true
        select.state shouldBe 2
    }

    // ── EN #4/#5: MangaKakalot и FlameComics — фильтров нет ─────────────────────

    @Test
    fun `mangakakalot and flamecomics expose no filters - best effort no injection`() = runTest {
        // Mangakakalot.kt / FlameComics.kt: getFilterList отсутствует, списочная
        // выдача без статуса (UNKNOWN) — инжект невозможен, пост-фильтр их пропускает
        // (задокументированный best-effort, лента не голодает).
        val applied = catalog.applyMangaStatusFilter(FilterList(), finishedOnly)
        applied shouldBe false
    }

    // ── RU #1: ReadManga (grouple) — TriStateGroup «Статус выхода» ──────────────

    @Test
    fun `readmanga tri state group gets Завершён included for finished`() = runTest {
        // grouple/Filters.kt:37 — TriStateGroup("Статус выхода", data);
        // опции — реальные статусы grouple (ru-названия с сервера).
        val options = listOf("Выходит" to "1", "Завершён" to "2", "Приостановлен" to "3", "Анонс" to "4")
        val tris = options.map { (name, id) -> TriStateFilter(name, id) }
        val group = object : Filter.Group<TriStateFilter>("Статус выхода", tris) {}
        val filters = FilterList(group)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe true
        tris.map { it.name to it.state } shouldBe listOf(
            "Выходит" to Filter.TriState.STATE_IGNORE,
            "Завершён" to Filter.TriState.STATE_INCLUDE,
            "Приостановлен" to Filter.TriState.STATE_IGNORE,
            "Анонс" to Filter.TriState.STATE_IGNORE,
        )
    }

    // ── RU #2: MangaLib (LibGroup) — Group<CheckBox> «Статус тайтла» ────────────

    @Test
    fun `mangalib status title group gets Завершён checked for finished`() = runTest {
        // LibsGroup.kt:539 — Filter.Group<CheckFilter>("Статус тайтла", titles);
        // опции — статусы тайтла API MangaLib (ru-названия).
        val boxes = listOf("Онгоинг", "Завершён", "Анонс", "Приостановлен").map { TestCheckBox(it) }
        val group = object : Filter.Group<TestCheckBox>("Статус тайтла", boxes) {}
        val filters = FilterList(group)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe true
        boxes.map { it.name to it.state } shouldBe listOf(
            "Онгоинг" to false,
            "Завершён" to true,
            "Анонс" to false,
            "Приостановлен" to false,
        )
    }

    // ── Развилки: «Статус перевода» ≠ статус выпуска; 2 статуса vs одиночный Select ──

    @Test
    fun `mangalib scanlate status group is not release status`() = runTest {
        // LibsGroup.kt:538 — StatusList("Статус перевода") — это статус СКАНА, не выпуска:
        // инжект обязан его пропустить (EXCLUDE-токен «перевода»).
        val boxes = listOf("Продолжается", "Завершён").map { TestCheckBox(it) }
        val group = object : Filter.Group<TestCheckBox>("Статус перевода", boxes) {}
        val filters = FilterList(group)

        val applied = catalog.applyMangaStatusFilter(filters, finishedOnly)

        applied shouldBe false
    }

    @Test
    fun `weebcentral single select with two selected statuses falls back to group search`() = runTest {
        // Одиночный Select при 2+ статусах не выразим (AsuraScans-форма):
        // инжект пропускается — ряд живёт пост-фильтром.
        val options = arrayOf("All", "Ongoing", "Completed", "Hiatus")
        val select = object : Filter.Select<String>("Status", options, 0) {}
        val filters = FilterList(select)

        val applied = catalog.applyMangaStatusFilter(
            filters,
            setOf(DiscoveryReleaseStatus.FINISHED, DiscoveryReleaseStatus.ONGOING),
        )

        applied shouldBe false
    }

    /** grouple TriStateFilter (Filters.kt:6). */
    private class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

    /** Конкретный CheckBox (source-api абстрактен, как в UriMultiSelectOption). */
    private class TestCheckBox(name: String) : Filter.CheckBox(name)
}

package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryReleaseStatus

/**
 * V4: эвристика статус-фильтра самих расширений (аналог жанровой [GenreMatcher]):
 * имена фильтров и опций любого языка сопоставляются с [DiscoveryReleaseStatus]
 * по синоним-таблицам, чтобы дисквери мог прокинуть выбранный пользователем статус
 * выпуска в `getSearchManga(1, "", filters)` источника.
 *
 * Формы статус-фильтров в экосистеме (extensions-source): Select/EnhancedSelect/
 * UriPartFilter (одиночный выбор), Group<CheckBox> (madara), Group<TriState> и
 * TriStateGroup (grouple), топ-левел TriState/CheckBox (zmanga).
 */
internal object SourceStatusFilterMatcher {

    /** Имя фильтра про статус выпуска («Status», «Статус», «Estado»…). «Статус перевода» — не он. */
    fun isStatusFilterName(name: String): Boolean {
        val n = name.lowercase()
        if (NAME_EXCLUDE_TOKENS.any { it in n }) return false
        return NAME_TOKENS.any { it in n }
    }

    /** Статус опции фильтра по её имени; null — название не распознано. */
    fun statusOfOption(name: String): DiscoveryReleaseStatus? {
        val n = name.lowercase()
        return OPTION_TOKENS.firstNotNullOfOrNull { (status, tokens) ->
            if (tokens.any { it in n }) status else null
        }
    }

    /**
     * Select (одиночный выбор): индекс опции под выбранный статус.
     * 2+ статусов одиночным Select не выразить — null (best-effort: инжект пропускается).
     */
    fun matchSelectIndex(optionNames: List<String>, selected: Set<DiscoveryReleaseStatus>): Int? {
        if (selected.size != 1) return null
        val want = selected.first()
        return optionNames.indexOfFirst { statusOfOption(it) == want }.takeIf { it >= 0 }
    }

    /** Group (чекбоксы/tri-state): имена опций, которые нужно включить под выбор. */
    fun matchOptionNames(optionNames: List<String>, selected: Set<DiscoveryReleaseStatus>): Set<String> =
        optionNames.filter { statusOfOption(it) in selected }.toSet()

    /**
     * Пост-фильтр latest-выдачи: статус айтема каталога (SManga/SAnime/SNovel.status)
     * к [DiscoveryReleaseStatus]; null = статус неизвестен/лицензия — такой айтем не режем
     * (правило Shikimori: «без статуса — не режем», ряд не голодает).
     */
    fun fromEntryStatus(status: Int): DiscoveryReleaseStatus? = when (status) {
        ENTRY_ONGOING -> DiscoveryReleaseStatus.ONGOING
        ENTRY_COMPLETED, ENTRY_PUBLISHING_FINISHED -> DiscoveryReleaseStatus.FINISHED
        ENTRY_CANCELLED, ENTRY_ON_HIATUS -> DiscoveryReleaseStatus.PAUSED
        else -> null
    }

    /** Айтем проходит пост-фильтр: выбор пуст ИЛИ статус неизвестен ИЛИ статус в выборе. */
    fun entryPasses(status: Int, selected: Set<DiscoveryReleaseStatus>): Boolean =
        selected.isEmpty() || fromEntryStatus(status)?.let { it in selected } != false

    /**
     * Статус по сырому значению провайдера (AniList «RELEASING», Shikimori «released»,
     * MAL «Currently Airing»…): нижний регистр, `_`/`-` → пробел, затем синоним-таблицы.
     * null — значение не распознано.
     */
    fun statusOfRaw(raw: String?): DiscoveryReleaseStatus? {
        val normalized = raw?.lowercase()
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return statusOfOption(normalized)
    }

    /**
     * Строгий проход рекомендации внешнего провайдера: выбор пуст — всё проходит;
     * иначе тайтл ОБЯЗАН нести распознанный статус из выбора. Провайдеры без статуса
     * (MAL/MU/NU similar) при активном фильтре в ряд не попадают — фильтр обязан
     * «реально работать», а не просачивать неизвестное.
     */
    fun rawStatusPasses(raw: String?, selected: Set<DiscoveryReleaseStatus>): Boolean {
        if (selected.isEmpty()) return true
        return statusOfRaw(raw) in selected
    }

    /** Пост-фильтр recommendations-выдачи строгим правилом [rawStatusPasses]. */
    fun <T> filterByRawStatus(
        items: List<T>,
        selected: Set<DiscoveryReleaseStatus>,
        rawOf: (T) -> String?,
    ): List<T> = if (selected.isEmpty()) {
        items
    } else {
        items.filter { rawStatusPasses(rawOf(it), selected) }
    }

    // Константы status моделей каталога (SManga/SAnime/SNovel делят схему).
    private const val ENTRY_ONGOING = 1
    private const val ENTRY_COMPLETED = 2
    private const val ENTRY_PUBLISHING_FINISHED = 4
    private const val ENTRY_CANCELLED = 5
    private const val ENTRY_ON_HIATUS = 6

    private val NAME_TOKENS = listOf(
        "status", "статус", "estado", "statut", "stato", "durum", "stan", "stav",
        "trạng thái", "สถานะ", "状态", "状態", "상태", "حالة",
    )

    private val NAME_EXCLUDE_TOKENS = listOf(
        "translation",
        "перевода",
        "traducc",
        "traduç",
        "traduction",
        "переклад",
    )

    // Порядок важен: ANONS первым (NOT_YET_RELEASED содержит «released», но это анонс),
    // затем FINISHED раньше ONGOING («Publishing finished» ≠ ongoing).
    private val OPTION_TOKENS: List<Pair<DiscoveryReleaseStatus, List<String>>> = listOf(
        DiscoveryReleaseStatus.ANONS to listOf(
            "not yet",
            "upcoming",
            "announced",
            "анонс",
            "запланир",
            "próxim",
            "à venir",
        ),
        DiscoveryReleaseStatus.FINISHED to listOf(
            "completed", "complete", "finished", "ended", "released", "заверш", "закончен",
            "finalizado", "finalisé", "terminé", "terminado", "finito", "completo",
            "abgeschlossen", "beendet", "zakończ", "完結", "완결", "hoàn thành",
            "tamamland", "selesai", "tamat",
        ),
        DiscoveryReleaseStatus.ONGOING to listOf(
            "ongoing", "airing", "releasing", "publishing", "онгоинг", "выходит",
            "продолж", "en emisión", "en cours", "in corso", "em lançamento", "lauf",
            "devam", "berlangsung", "trwa", "трива", "連載", "연재", "đang",
        ),
        DiscoveryReleaseStatus.PAUSED to listOf(
            "hiatus", "paused", "pause", "приостанов", "пауза", "en pausa", "en pause",
            "in pausa", "pausado", "discontinu", "cancel", "休載", "휴재", "暂停", "暫停",
        ),
    )
}

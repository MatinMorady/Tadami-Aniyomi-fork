package eu.kanade.tachiyomi.ui.discovery

import tachiyomi.domain.discovery.model.DiscoverySuggestion

/** Решение по тапу на plugin-bound карточку ленты «Для тебя». */
internal sealed interface DirectOpenDecision {
    /** Полная привязка: открыть экран тайтла напрямую (источник [sourceId], адрес [url]). */
    data class Open(val sourceId: Long, val url: String) : DirectOpenDecision

    /** Привязки нет или источник не установлен: показать прежнюю шторку предпросмотра. */
    data object Sheet : DirectOpenDecision
}

/** Прямое открытие только при полной привязке и установленном (не стаб) источнике. */
internal fun decideDirectOpen(
    item: DiscoverySuggestion,
    isSourceInstalled: (Long) -> Boolean,
): DirectOpenDecision {
    val sourceId = item.sourceId
    val url = item.sourceUrl
    // Пустой/blank url — битая привязка: материалзовать мусор в БД нельзя, уходим в шторку.
    return if (sourceId != null && !url.isNullOrBlank() && isSourceInstalled(sourceId)) {
        DirectOpenDecision.Open(sourceId, url)
    } else {
        DirectOpenDecision.Sheet
    }
}

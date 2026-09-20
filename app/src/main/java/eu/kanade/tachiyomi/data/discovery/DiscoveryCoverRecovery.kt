package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/**
 * Lazy-восстановление отсутствующих обложек discovery-карточек (NovelUpdates и др.
 * провайдеры без thumbnail): один фоновый meta-запрос на тайтл, результат живёт
 * в памяти до смены ленты. Без миграции схемы — URL подставляется в
 * AuroraPosterRequest (primary для null-обложек, fallback для будущих триггеров).
 */
object DiscoveryCoverRecovery {
    private val cache = DiscoveryLruCache<String, String>(300)
    private val attempted = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    private fun key(mediaType: DiscoveryMediaType, cleanTitle: String) = "${mediaType.key}:$cleanTitle"

    fun get(mediaType: DiscoveryMediaType, cleanTitle: String): String? = cache.get(key(mediaType, cleanTitle))

    fun put(mediaType: DiscoveryMediaType, cleanTitle: String, url: String) {
        cache.put(key(mediaType, cleanTitle), url)
    }

    /** Защита от повторных запросов по тайтлам, у которых меты нет (null-cover провайдеры). */
    fun markAttempted(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
    ): Boolean = attempted.add(key(mediaType, cleanTitle))

    /** Отмена запроса — не попытка: снять метку, чтобы следующий sweep повторил. */
    fun forgetAttempted(mediaType: DiscoveryMediaType, cleanTitle: String) {
        attempted.remove(key(mediaType, cleanTitle))
    }

    fun clear() {
        cache.clear()
        attempted.clear()
    }
}

internal fun coverRecoveryTargets(items: List<DiscoverySuggestion>): List<DiscoverySuggestion> =
    items.filter { it.coverUrl.isNullOrBlank() }

internal suspend fun recoverMissingCovers(
    items: List<DiscoverySuggestion>,
    mediaType: DiscoveryMediaType,
    trendingSource: DiscoveryTrendingSource,
    limit: Int = eu.kanade.tachiyomi.ui.discovery.META_PREFETCH_COUNT,
    onRecovered: () -> Unit = {},
): Int {
    var recovered = 0
    coverRecoveryTargets(items).take(limit).forEach { item ->
        if (!DiscoveryCoverRecovery.markAttempted(mediaType, item.cleanTitle)) return@forEach
        val meta = try {
            trendingSource.fetchMeta(item.title, mediaType)
        } catch (e: CancellationException) {
            DiscoveryCoverRecovery.forgetAttempted(mediaType, item.cleanTitle)
            throw e
        } catch (e: Exception) {
            null
        }
        val cover = meta?.coverUrl?.takeIf { it.isNotBlank() } ?: return@forEach
        DiscoveryCoverRecovery.put(mediaType, item.cleanTitle, cover)
        recovered++
        onRecovered()
    }
    return recovered
}

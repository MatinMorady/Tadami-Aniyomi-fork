package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

/**
 * Lazy-восстановление отсутствующих обложек discovery-карточек (NovelUpdates и др.
 * провайдеры без thumbnail): один фоновый meta-запрос на тайтл, результат живёт
 * в памяти всё время процесса (clear() вызывается только из тестов). Без миграции
 * схемы — URL подставляется в
 * AuroraPosterRequest (primary для null-обложек, fallback для будущих триггеров).
 */
object DiscoveryCoverRecovery {
    private const val MAX_RECOVERY_ATTEMPTS = 2

    private val cache = DiscoveryLruCache<String, String>(300)

    // Гоночные инкременты нестрогие — худший случай лишний retry, шторма сети это не даёт.
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun key(mediaType: DiscoveryMediaType, cleanTitle: String) = "${mediaType.key}:$cleanTitle"

    fun get(mediaType: DiscoveryMediaType, cleanTitle: String): String? = cache.get(key(mediaType, cleanTitle))

    fun put(mediaType: DiscoveryMediaType, cleanTitle: String, url: String) {
        cache.put(key(mediaType, cleanTitle), url)
    }

    /** Одноразовая защита от шторма; transient-сбой (оффлайн) прощает одну повторную попытку на следующем sweep. */
    fun markAttempted(
        mediaType: DiscoveryMediaType,
        cleanTitle: String,
    ): Boolean {
        val key = key(mediaType, cleanTitle)
        val current = attempts[key] ?: 0
        if (current >= MAX_RECOVERY_ATTEMPTS) return false
        attempts[key] = current + 1
        return true
    }

    /** Отмена запроса — не попытка: снять счёт полностью, следующий sweep повторит. */
    fun forgetAttempted(mediaType: DiscoveryMediaType, cleanTitle: String) {
        attempts.remove(key(mediaType, cleanTitle))
    }

    fun clear() {
        cache.clear()
        attempts.clear()
    }
}

internal const val META_PREFETCH_COUNT = 8

internal fun coverRecoveryTargets(items: List<DiscoverySuggestion>): List<DiscoverySuggestion> =
    items.filter { it.coverUrl.isNullOrBlank() }

internal suspend fun recoverMissingCovers(
    items: List<DiscoverySuggestion>,
    mediaType: DiscoveryMediaType,
    trendingSource: DiscoveryTrendingSource,
    limit: Int = META_PREFETCH_COUNT,
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

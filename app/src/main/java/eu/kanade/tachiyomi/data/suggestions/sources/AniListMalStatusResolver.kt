package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Пакетный резолв статуса выпуска по MAL id через AniList GraphQL
 * (`Page { media(idMal_in: [...]) { idMal status } }`) — один запрос на партию.
 *
 * Зачем: MAL-рекомендации (Jikan /recommendations) статуса не несут, и при
 * активном статус-фильтре ленты строгое правило их отбрасывало бы. Пакетный
 * резолв возвращает им статус из AniList (у AniList тот же MAL id в `idMal`),
 * и фильтр работает честно, не выпиливая MAL-ряд целиком.
 *
 * Деградация: любая ошибка сети/парсинга → пустая карта (кандидаты остаются
 * без статуса и по-прежнему отсекаются фильтром — безопасно).
 */
object AniListMalStatusResolver {

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }

    // In-memory кэш: idMal → status (статус меняется редко; перезапрос на процесс достаточен).
    // ConcurrentHashMap: резолв вызывается из параллельных корутин (per-seed async в координаторе).
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /**
     * Карта idMal → сырой статус AniList («RELEASING»/«FINISHED»/…).
     * [type] — «ANIME» (у MAL свои id-пространства для аниме и манги, тип обязателен).
     */
    suspend fun resolveByMalIds(malIds: List<Long>, type: String = "ANIME"): Map<Long, String> {
        val distinct = malIds.distinct().filter { it > 0 }
        if (distinct.isEmpty()) return emptyMap()

        val cached = distinct.mapNotNull { id -> cache[id]?.let { id to it } }.toMap()
        val missing = distinct.filter { id -> !cache.containsKey(id) }
        if (missing.isEmpty()) return cached

        val resolved = runCatching { queryStatuses(missing, type) }.getOrDefault(emptyMap())
        cache.putAll(resolved)
        return cached + resolved
    }

    private suspend fun queryStatuses(malIds: List<Long>, type: String): Map<Long, String> {
        val query = """
            query (${'$'}ids: [Int], ${'$'}type: MediaType) {
              Page(page: 1, perPage: 50) {
                media(idMal_in: ${'$'}ids, type: ${'$'}type) { idMal status }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("type", type)
                    put(
                        "ids",
                        buildJsonArray { malIds.take(50).forEach { add(it.toInt()) } },
                    )
                },
            )
        }
        return try {
            AniListRequestGuard.ensureClosed()
            AniListRequestGuard.acquire()
            val page = client
                .newCall(
                    POST(
                        "https://graphql.anilist.co/",
                        headers = AniListRequestGuard.headers,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                .awaitSuccess()
                .parseAs<JsonObject>(json)
            (((page["data"] as? JsonObject)?.get("Page") as? JsonObject)?.get("media") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { media ->
                    val idMal = media["idMal"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val status = media["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    idMal to status
                }
                ?.toMap()
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("403") == true) AniListRequestGuard.reportForbidden()
            logcat { "[AniListMalStatusResolver] FAILED: ${e.message}" }
            emptyMap()
        }
    }
}

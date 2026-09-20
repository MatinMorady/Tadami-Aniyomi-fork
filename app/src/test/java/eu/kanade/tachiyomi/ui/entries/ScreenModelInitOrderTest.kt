package eu.kanade.tachiyomi.ui.entries

import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeLessThan
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression guard for the production crash in 0.62 (build 209): an init-launched IO coroutine
 * in MangaScreenModel read `recentMangaUpdateCache` before the constructor assigned it
 * (Kotlin runs property initializers in declaration order and `init` was declared first),
 * producing `NullPointerException: TtlCache.get(java.lang.Long) on a null object reference`.
 *
 * Every field listed below is read or written by code the `init` block launches (or by `init`
 * itself) and MUST be declared above `init`: then the initializer runs before any coroutine is
 * dispatched, and the dispatch itself provides the happens-before edge for safe publication.
 * Fields declared after `init` either race the coroutines (null reads on weak-memory devices)
 * or — when `init` assigns them synchronously — get silently wiped by their own initializer.
 */
class ScreenModelInitOrderTest {

    private val guardedFields = mapOf(
        "eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt" to listOf(
            "sourceUpdateMutex",
            "recentMangaUpdateCache",
        ),
        "eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt" to listOf(
            "recentAnimeDetailsCache",
        ),
        "eu/kanade/tachiyomi/ui/entries/novel/NovelScreenModel.kt" to listOf(
            "suggestionSeedUsed",
            "suggestionsJob",
            "recentChapterListCache",
            "recentNovelDetailsCache",
            "restoredDownloadedChapterIds",
            "downloadBatchCollectionJob",
            "downloadBatchLock",
            "pendingDownloadBatchEvents",
        ),
    )

    @Test
    fun `fields touched by init coroutines are declared before the init block`() {
        guardedFields.forEach { (relativePath, fields) ->
            val lines = readSource(relativePath)
            val initLine = lines.lineNumber(Regex("^    init \\{"), "$relativePath: init block")
            fields.forEach { field ->
                val declarationLine = lines.lineNumber(
                    Regex("^    (?:private |internal )?(?:val|var|lateinit var) $field\\b"),
                    "$relativePath: declaration of `$field`",
                )
                withClue(
                    "$relativePath: `$field` is declared on line $declarationLine, after `init` " +
                        "(line $initLine) - init-launched coroutines can observe it uninitialized: ",
                ) {
                    declarationLine shouldBeLessThan initLine
                }
            }
        }
    }

    private fun readSource(relativePath: String): List<String> {
        val appScoped = File("app/src/main/java/$relativePath")
        val moduleScoped = File("src/main/java/$relativePath")
        val target = when {
            appScoped.exists() -> appScoped
            moduleScoped.exists() -> moduleScoped
            else -> error("Source file not found from the test working directory: $relativePath")
        }
        return target.readLines()
    }

    private fun List<String>.lineNumber(regex: Regex, what: String): Int {
        val index = indexOfFirst { regex.containsMatchIn(it) }
        require(index >= 0) { "Not found - $what (pattern: $regex)" }
        return index + 1
    }
}

package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * M2: fallback перевода для жанров источников. Тестируется на фейковом
 * переводчике с ВЫДУМАННЫМИ названиями (реальные турецкие «Aksiyon»/«Macera»
 * онтология уже знает напрямую — Wikidata отдаёт tr-алиасы). Сеть в тестах
 * не участвует. Дисциплина: ошибка перевода, офлайн и бюджет не ломают выдачу.
 */
class GenreTranslationFallbackTest {

    private val fallback = GenreTranslationFallback(
        translate = { name ->
            when (name) {
                "Jaqenai" -> "action"
                "Voltras" -> "adventure"
                else -> null
            }
        },
    )

    @Test
    fun `неизвестный чекбокс матчится через перевод`() = runBlocking {
        val result = fallback.selectSourceGenres(
            userGenres = listOf("экшен"),
            sourceGenreNames = listOf("Action", "Jaqenai", "Drama"),
        )
        result.shouldContainExactly("Action", "Jaqenai")
    }

    @Test
    fun `переводятся только непонятые названия`() = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            if (name ==
                "Voltras"
            ) {
                "adventure"
            } else {
                null
            }
        })
        val result = fb.selectSourceGenres(
            userGenres = listOf("приключения", "драма"),
            sourceGenreNames = listOf("Adventure", "Voltras", "Drama"),
        )
        result.shouldContainExactly("Adventure", "Voltras", "Drama")
        // Adventure и Drama поняты напрямую — в перевод уходит только Voltras.
        calls.shouldContainExactly("Voltras")
    }

    @Test
    fun `перевод кэшируется — повторный вызов не ходит в сеть`() = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            if (name ==
                "Jaqenai"
            ) {
                "action"
            } else {
                null
            }
        })
        fb.selectSourceGenres(listOf("экшен"), listOf("Jaqenai"))
        fb.selectSourceGenres(listOf("экшен"), listOf("Jaqenai"))
        calls.shouldContainExactly("Jaqenai")
    }

    @Test
    fun `ошибка переводчика не ломает выдачу`() = runBlocking {
        val fb = GenreTranslationFallback(translate = { throw IllegalStateException("offline") })
        val result = fb.selectSourceGenres(listOf("экшен"), listOf("Action", "Jaqenai"))
        result.shouldContainExactly("Action")
    }

    @Test
    fun `офлайн перевод — только прямой матчинг`() = runBlocking {
        val fb = GenreTranslationFallback(translate = { null })
        val result = fb.selectSourceGenres(listOf("экшен", "приключения"), listOf("Action", "Jaqenai"))
        result.shouldContainExactly("Action")
    }

    @Test
    fun `бюджет переводов ограничен лавиной экзотики`() = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(
            translate = { name ->
                calls += name
                null
            },
            maxTranslationsPerCall = 3,
        )
        val exotic = (1..20).map { "Turkce$it" }
        val result = fb.selectSourceGenres(listOf("экшен"), exotic)
        result.shouldBeEmpty()
        calls.size shouldBe 3
    }

    @Test
    fun `пустые пользовательские жанры не запускают переводы`() = runBlocking {
        val calls = mutableListOf<String>()
        val fb = GenreTranslationFallback(translate = { name ->
            calls += name
            "action"
        })
        val result = fb.selectSourceGenres(listOf("  "), listOf("Jaqenai"))
        result.shouldBeEmpty()
        calls.shouldBeEmpty()
    }
}

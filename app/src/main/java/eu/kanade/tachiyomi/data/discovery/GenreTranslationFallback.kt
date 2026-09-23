package eu.kanade.tachiyomi.data.discovery

import java.util.concurrent.ConcurrentHashMap

/**
 * M2: сетевой fallback матчинга жанров. Чекбокс источника, который не сматчился
 * напрямую (например, турецкое «Aksiyon»), переводится один раз через подключаемый
 * переводчик и матчится повторно. Кэш в памяти на процесс — повторные феты
 * источника переводов не требуют.
 *
 * Дисциплина деградации: ошибка/офлайн перевода — молча пропускаем, остаётся
 * детерминированное ядро [GenreMatcher]; бюджет переводов на вызов ограничен,
 * чтобы экзотический источник не устроил лавину сетевых запросов.
 */
class GenreTranslationFallback(
    private val translate: suspend (String) -> String?,
    private val maxTranslationsPerCall: Int = 12,
) {

    private val cache = ConcurrentHashMap<String, List<String>>()

    /**
     * Чекбоксы источника под пользовательские жанры (в порядке источника):
     * сначала прямой матчинг, затем перевод непонятых названий и повторный
     * матчинг переведённых форм.
     */
    suspend fun selectSourceGenres(
        userGenres: List<String>,
        sourceGenreNames: List<String>,
    ): List<String> {
        val direct = GenreMatcher.selectSourceGenres(userGenres, sourceGenreNames).toHashSet()
        val wanted = userGenres.filter { it.isNotBlank() }
        val unmatched = sourceGenreNames.filter { it.isNotBlank() && it !in direct }
        if (wanted.isEmpty() || unmatched.isEmpty()) return direct.toList()

        var budget = maxTranslationsPerCall
        val translatedMatches = mutableSetOf<String>()
        for (name in unmatched) {
            val forms = cachedForms(name, spend = { budget-- })
            if (wanted.any { user -> forms.any { form -> GenreMatcher.matches(user, form) } }) {
                translatedMatches += name
            }
        }
        return sourceGenreNames.filter { it.isNotBlank() && (it in direct || it in translatedMatches) }
    }

    /** Формы названия: оригинал + перевод; в кэш — один раз, перевод тратится один раз. */
    private suspend fun cachedForms(name: String, spend: () -> Unit): List<String> {
        cache[name]?.let { return it }
        val forms = mutableListOf(name)
        val translation = runCatching { translate(name) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != name }
        if (translation != null) {
            forms += translation
        }
        cache[name] = forms
        spend()
        return forms
    }
}

package tachiyomi.domain.discovery.model

/**
 * Taste Learning Engine: вид сигнала взаимодействия с карточкой подборки.
 * Порядок enum = старшинство: более сильный сигнал перезаписывает слабый
 * при повторной записи на тот же тайтл (hide побеждает add и т.д.).
 *
 * CONSUMED — «просмотрено/прочитано»: нейтральное исключение из ленты
 * (тайтл больше не показывается, инкогнито в т.ч.), вес 0 — вкусовой
 * профиль не трогает. Старшинство между like и add: перекрывает клик/лайк,
 * но не добавление в библиотеку и не скрытие.
 *
 * SHOWN_IGNORED — неявный негатив «показано ≥3 раз за 48ч, ни разу не
 * кликнуто»: самый младший (ordinal 0), любой явный сигнал перекрывает.
 * Не пишется в БД — синтезируется в Runner из счётчика показов.
 */
enum class DiscoverySignalType(val key: String, val weight: Double) {
    SHOWN_IGNORED("shown_ignored", -0.15),
    CLICK("click", 0.4),
    LIKE("like", 0.8),
    CONSUMED("consumed", 0.0),
    ADD("add", 1.0),
    HIDE("hide", -1.0),
    ;

    companion object {
        fun fromKey(key: String?): DiscoverySignalType? = entries.firstOrNull { it.key == key }

        /** Новый сигнал перезаписывает записанный, только если он сильнее или равен. */
        fun overrides(previous: DiscoverySignalType?, next: DiscoverySignalType): Boolean =
            previous == null || next.ordinal >= previous.ordinal
    }
}

/** Строка `discovery_signals`: снимок взаимодействия с тайтлом подборки. */
data class DiscoverySignal(
    val mediaType: DiscoveryMediaType,
    val cleanTitle: String,
    val title: String,
    val signalType: DiscoverySignalType,
    val genres: List<String>,
    val provider: String?,
    val sourceKey: String?,
    val createdAt: Long,
)

package tachiyomi.domain.discovery.model

/**
 * Taste Learning Engine: вид сигнала взаимодействия с карточкой подборки.
 * Порядок enum = старшинство: более сильный сигнал перезаписывает слабый
 * при повторной записи на тот же тайтл (hide побеждает add и т.д.).
 */
enum class DiscoverySignalType(val key: String, val weight: Double) {
    CLICK("click", 0.4),
    LIKE("like", 0.8),
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

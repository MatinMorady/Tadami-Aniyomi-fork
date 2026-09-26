package eu.kanade.presentation.track

internal fun resolveReadOrdinal(itemNumbers: List<Double>, lastRead: Double): Int? {
    if (itemNumbers.isEmpty()) return null
    return itemNumbers.count { it > 0.0 && it <= lastRead }
}

internal fun trackProgressText(
    ordinal: Int?,
    lastRead: Double,
    total: Long,
    format: (Double) -> String,
): String {
    val head = ordinal?.toString() ?: format(lastRead)
    return if (total > 0) "$head / $total" else head
}

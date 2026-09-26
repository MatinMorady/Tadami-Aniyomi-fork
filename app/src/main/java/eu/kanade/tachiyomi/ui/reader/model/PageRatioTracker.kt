package eu.kanade.tachiyomi.ui.reader.model

/**
 * Keeps the most recent page height/width ratios and exposes their median. Used to estimate the
 * placeholder height of pages whose pixel size is not known yet, so items keep a stable height
 * while their image is still loading. Main thread only.
 */
class PageRatioTracker(private val capacity: Int = MAX_KNOWN_PAGE_RATIOS) {

    private val ratios = ArrayList<Float>(capacity)

    /** Median of the noted ratios, or null while nothing is known yet. */
    var typical: Float? = null
        private set

    fun note(ratio: Float) {
        if (ratio <= 0f) return
        if (ratios.size == capacity) ratios.removeAt(0)
        ratios.add(ratio)
        typical = ratios.sorted()[ratios.size / 2]
    }

    private companion object {
        private const val MAX_KNOWN_PAGE_RATIOS = 16
    }
}

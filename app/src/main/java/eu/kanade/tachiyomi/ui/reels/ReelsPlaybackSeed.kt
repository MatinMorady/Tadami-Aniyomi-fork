package eu.kanade.tachiyomi.ui.reels

import tachiyomi.domain.reels.anime.model.ReelsFavorite

/**
 * One-shot playback seed (audit H10 / B4): tapping a watch-history entry must play THAT clip,
 * even when it is not favorited and the live feed is shuffled. The history screen converts the
 * entry into a favorite-shaped row and leaves it here; the feed model's offline-playlist init
 * consumes it and puts the clip at the head of the playlist (favorites follow as the queue).
 * A process-global slot keeps the Voyager screen parameters small and saved-state safe.
 */
object ReelsPlaybackSeed {

    @Volatile
    var pending: ReelsFavorite? = null

    /** Returns the seed exactly once; later reads (screen recreation) get null. */
    fun consume(): ReelsFavorite? {
        val seed = pending
        pending = null
        return seed
    }
}

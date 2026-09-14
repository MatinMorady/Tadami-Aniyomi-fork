package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (feed contract v22): a feed source that can re-resolve a video's
 * playable URL by its stable item id. Service CDN links are typically short-lived, while saved
 * favorites (and the offline playlist replaying them) store the URL captured at like-time.
 *
 * Instanceof-detected (the marker-interface pattern; no default members on existing
 * interfaces). Sources without it keep storing and replaying the original URLs. The host calls
 * this lazily, keeps the stored URL on null/failure, and never surfaces resolver errors.
 */
interface AnimeFeedVideoResolverSource {

    /**
     * Fresh playable URL for the given item, or null when the id is unknown/expired. [hd] asks
     * for the upgraded variant; the base variant of the same item is the natural fallback.
     */
    suspend fun resolveVideoUrl(itemId: String, hd: Boolean): String?
}

package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage

/**
 * Capability interface (contract v23): a feed source whose site search is album-oriented and
 * can answer with a PAGINATED directory of albums matching the query (bunkr-like sources).
 *
 * The host detects support with `source is AnimeAlbumSearchSource` (instanceof, same marker
 * pattern as the other optional caps). When present, the reels search renders album cards
 * (open album / save to collection / long-press preview) instead of the flat video feed;
 * sources without the capability keep the pre-v23 search behavior untouched.
 *
 * Pagination follows the v17 sticky-cursor protocol on its own stream; an empty terminal
 * page (hasNextPage=false) ends the result list. A query with no matches returns an empty
 * page, never a throw.
 */
interface AnimeAlbumSearchSource {

    /** One page of albums matching [query]. */
    suspend fun searchAlbums(query: String, page: Int, cursor: String?): FeedCategoryPage
}

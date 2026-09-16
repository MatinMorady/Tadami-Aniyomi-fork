package eu.kanade.tachiyomi.ui.reader.novel.tts

interface NativeScrollTtsNavigator {
    suspend fun scrollToBlock(blockIndex: Int, scrollOffsetPx: Int = 0)
}

class NativeScrollTtsNavigationAdapter(
    private val navigator: NativeScrollTtsNavigator,
) : NovelTtsNavigationAdapter {
    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        // The chapter-title segment carries the sourceBlockIndex = -1 sentinel: it has no content
        // block to scroll to. Follow it at the top of the chapter, matching the WebView surface
        // (title maps to 0% progress). LazyListState.scrollToItem throws on a negative index.
        navigator.scrollToBlock(segment.sourceBlockIndex.coerceAtLeast(0))
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        return NovelTtsNavigationAnchor(
            blockIndex = blockIndex,
            scrollOffsetPx = scrollOffsetPx,
        )
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        anchor.blockIndex?.let { navigator.scrollToBlock(it, anchor.scrollOffsetPx) }
    }
}

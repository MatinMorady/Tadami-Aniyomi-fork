package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import mihon.core.archive.ArchiveReader
import tachiyomi.core.common.util.system.ImageUtil
import java.io.IOException

/**
 * Loader used to load a chapter from an archive file.
 */
internal class ArchivePageLoader(private val reader: ArchiveReader) : PageLoader() {
    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> = reader.useEntries { entries ->
        entries
            // isImage() checks the extension first and only calls the stream lambda for unknown
            // extensions (magic-byte sniffing). The old code opened a stream per entry upfront -
            // and every getInputStream() rescans the archive from the start - making chapter
            // open O(N^2). Now the rescan only happens for the rare unknown-extension entry.
            .filter {
                it.isFile &&
                    ImageUtil.isImage(it.name) {
                        reader.getInputStream(it.name)
                            ?: throw IOException("Archive reader is no longer available")
                    }
            }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = {
                        reader.getInputStream(entry.name)
                            ?: throw IOException("Archive reader is no longer available")
                    }
                    status = Page.State.READY
                }
            }
            .toList()
    }

    override suspend fun loadPage(page: ReaderPage) {
        if (isRecycled) return
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}

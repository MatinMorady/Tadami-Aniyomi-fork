package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.source.local.entries.manga.isLocal

class ReaderTransitionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private var data: Data? by mutableStateOf(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadCheckJob: Job? = null

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun bind(
        transition: ChapterTransition,
        downloadManager: MangaDownloadManager,
        manga: Manga?,
        visibleChapterGap: Int? = null,
    ) {
        downloadCheckJob?.cancel()
        data = if (manga != null) {
            Data(
                transition = transition,
                currChapterDownloaded = transition.from.pageLoader?.isLocal == true,
                goingToChapterDownloaded = manga.isLocal(),
                visibleChapterGap = visibleChapterGap,
            )
        } else {
            null
        }

        // The disk-backed skipCache check must not run on the main thread: this view is bound
        // during holder creation at chapter borders and on first entry into the reader.
        val goingToChapter = transition.to?.chapter
        if (manga != null && goingToChapter != null && !manga.isLocal()) {
            downloadCheckJob = scope.launch {
                val downloaded = withContext(Dispatchers.IO) {
                    downloadManager.isChapterDownloaded(
                        chapterName = goingToChapter.name,
                        chapterScanlator = goingToChapter.scanlator,
                        mangaTitle = manga.title,
                        sourceId = manga.source,
                        skipCache = true,
                        mangaId = manga.id,
                        chapterId = goingToChapter.id,
                    )
                }
                // A rebind or detach cancels this job; never write into a stale bind.
                if (isActive) {
                    data = data?.copy(goingToChapterDownloaded = downloaded)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        downloadCheckJob?.cancel()
    }

    @Composable
    override fun Content() {
        data?.let {
            TachiyomiTheme {
                // B-A9: the view is hosted in a reader-themed context (night mode follows the
                // reader's black/gray/white background), but TachiyomiTheme resolves the APP
                // palette: app-dark + white reader background rendered light text on a light
                // transition background. Pick the content color from the host context's night
                // mode so it always contrasts with the reader background.
                val ctx = LocalContext.current
                val isDarkReaderBackground = remember(ctx) {
                    (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                }
                val readerContentColor = if (isDarkReaderBackground) Color(0xDEFFFFFF) else Color(0xDE000000)
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides readerContentColor,
                ) {
                    ChapterTransition(
                        transition = it.transition,
                        currChapterDownloaded = it.currChapterDownloaded,
                        goingToChapterDownloaded = it.goingToChapterDownloaded,
                        visibleChapterGap = it.visibleChapterGap,
                    )
                }
            }
        }
    }
    private data class Data(
        val transition: ChapterTransition,
        val currChapterDownloaded: Boolean,
        val goingToChapterDownloaded: Boolean,
        val visibleChapterGap: Int?,
    )
}

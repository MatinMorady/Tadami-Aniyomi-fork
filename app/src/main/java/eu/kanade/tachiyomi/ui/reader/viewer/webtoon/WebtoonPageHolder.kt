package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tadami.aurora.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ProcessedPageImage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressIndicator = createProgressIndicator()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    private val scope = MainScope()

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    init {
        refreshLayoutParams()

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { markDecodeError() }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        loadJob?.cancel()
        loadJob = scope.launch { loadPageAndProcessStatus() }
        refreshLayoutParams()
        refreshPlaceholderHeight()
    }

    private fun refreshLayoutParams() {
        val margin = (Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)).toInt()
        val bottomMargin = if (!viewer.isContinuous) 15.dpToPx else 0

        // Avoid layout thrash: rebinds while scrolling must not trigger a requestLayout
        // when nothing about the layout params actually changed.
        val current = frame.layoutParams as? FrameLayout.LayoutParams
        if (current != null &&
            current.marginStart == margin &&
            current.marginEnd == margin &&
            current.bottomMargin == bottomMargin
        ) {
            return
        }

        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            this.bottomMargin = bottomMargin
            marginEnd = margin
            marginStart = margin
        }
    }

    /**
     * Keeps the loading placeholder matched to the current viewport height. The container is
     * sized once at holder creation, when the recycler may not be measured yet (height 0) or may
     * have been resized since (rotation, split screen). A zero or stale placeholder height makes
     * the layout manager create and bind far more holders than needed and makes content jump
     * when the real image height arrives.
     */
    private fun refreshPlaceholderHeight() {
        val height = parentHeight
        val params = progressContainer.layoutParams ?: return
        if (height > 0 && params.height != height) {
            progressContainer.updateLayoutParams { this.height = height }
            progressIndicator.updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = height / 4)
            }
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        loadJob?.cancel()
        loadJob = null

        removeErrorLayout()
        frame.recycle()
        progressIndicator.setProgress(0)
        progressContainer.isVisible = true
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if there is no page or the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.QUEUE -> setQueued()
                    Page.State.LOAD_PAGE -> setLoading()
                    Page.State.DOWNLOAD_IMAGE -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator.setProgress(value)
                        }
                    }
                    Page.State.READY -> setImage()
                    Page.State.ERROR -> setError()
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator.setProgress(0)

        val streamFn = page?.stream ?: return

        try {
            val prepared = withIOContext {
                when (val processed = streamFn().use { process(Buffer().readFrom(it)) }) {
                    is ProcessedPageImage.Decoded -> PageImageData.Decoded(processed.bitmap)
                    is ProcessedPageImage.Encoded -> {
                        val source = processed.source
                        val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                        // Sniff image headers here so the UI thread does not have to instantiate
                        // native decoders per page while the user is scrolling.
                        val isTall = !isAnimated && ImageUtil.isTallImage(source)
                        val canUseHardware = !isAnimated && ImageUtil.canUseHardwareBitmap(source)
                        PageImageData.Encoded(source, isAnimated, isTall, canUseHardware)
                    }
                }
            }
            withUIContext {
                when (prepared) {
                    is PageImageData.Encoded -> {
                        frame.setImage(
                            prepared.source,
                            prepared.isAnimated,
                            ReaderPageImageView.Config(
                                zoomDuration = viewer.config.doubleTapAnimDuration,
                                minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                cropBorders = viewer.config.imageCropBorders,
                                webtoonSmartFit = viewer.config.webtoonSmartFit,
                                isTallImage = prepared.isTall,
                                canUseHardwareBitmap = prepared.canUseHardware,
                            ),
                        )
                    }
                    is PageImageData.Decoded -> {
                        // Split-and-merge/rotate results arrive pre-decoded: hand the bitmap
                        // straight to the image view instead of a JPEG q=100 re-encode plus
                        // second decode on the scroll path.
                        val bitmap = prepared.bitmap
                        frame.setImage(
                            BitmapDrawable(frame.resources, bitmap),
                            ReaderPageImageView.Config(
                                zoomDuration = viewer.config.doubleTapAnimDuration,
                                minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                cropBorders = viewer.config.imageCropBorders,
                                webtoonSmartFit = viewer.config.webtoonSmartFit,
                                isTallImage = bitmap.width > 0 &&
                                    bitmap.height.toFloat() / bitmap.width.toFloat() > 3F,
                            ),
                        )
                    }
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                markDecodeError()
            }
        }
    }

    private fun process(imageSource: BufferedSource): ProcessedPageImage {
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (viewer.config.dualPageSplit) {
            val isDoublePage = ImageUtil.isWideImage(imageSource)
            if (isDoublePage) {
                val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                // peek() keeps the source intact so the stream variant remains a viable fallback.
                ImageUtil.splitAndMergeBitmap(imageSource.peek(), upperSide)?.let {
                    return ProcessedPageImage.Decoded(it)
                }
                return ProcessedPageImage.Encoded(ImageUtil.splitAndMerge(imageSource, upperSide))
            }
        }

        return ProcessedPageImage.Encoded(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return ProcessedPageImage.Encoded(imageSource)
        }
        val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
        return ImageUtil.rotateImageBitmap(imageSource.peek(), rotation)
            ?.let { ProcessedPageImage.Decoded(it) }
            ?: ProcessedPageImage.Encoded(ImageUtil.rotateImage(imageSource, rotation))
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.isVisible = false
        initErrorLayout()
    }

    /**
     * Decode/render failure while the page status is READY: move the status to ERROR so the
     * Retry button can re-queue it through the page loader. Showing the error layout alone left
     * the status at READY and retryPage() became a no-op (the loader skips non-QUEUE pages).
     */
    private fun markDecodeError() {
        val currentPage = page
        if (currentPage != null && currentPage.status == Page.State.READY) {
            currentPage.status = Page.State.ERROR
        }
        setError()
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
        page?.let(viewer::onPageImageReady)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressIndicator(context).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = parentHeight / 4)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initErrorLayout(): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(
                MATCH_PARENT,
                (parentHeight * 0.8).toInt(),
            )
            errorLayout?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
        }

        val imageUrl = page?.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl)
                    context.startActivity(intent)
                }
            }
        }

        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }
}

private sealed interface PageImageData {
    data class Encoded(
        val source: BufferedSource,
        val isAnimated: Boolean,
        val isTall: Boolean,
        val canUseHardware: Boolean,
    ) : PageImageData

    data class Decoded(val bitmap: android.graphics.Bitmap) : PageImageData
}

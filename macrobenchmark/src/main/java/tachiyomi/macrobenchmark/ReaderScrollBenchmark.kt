package tachiyomi.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.tadami.aurora.benchmark"
private const val READER_ACTIVITY = "eu.kanade.tachiyomi.ui.reader.ReaderActivity"

/**
 * Measures frame timing while fling-scrolling the reader.
 *
 * The chapter is opened directly through ReaderActivity's intent (extras "manga"/"chapter"),
 * so no fragile UI navigation is involved. Ids come from instrumentation arguments:
 *
 * ```
 * ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *     -PbenchRules=Macrobenchmark -PbenchMangaId=<id> -PbenchChapterId=<id>
 * ```
 *
 * (wired in macrobenchmark/build.gradle.kts; without ids the test skips itself).
 * Use a WEBTOON-mode chapter to measure scroll smoothness and any chapter to measure
 * pager swipes - the same fling gesture drives both readers. Run the identical command
 * before/after a change to get an A/B comparison; results land in
 * macrobenchmark/build/outputs/androidTest-results/connected/ (frameDurationCpuMs,
 * frameOverrunMs percentiles).
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class ReaderScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val args = InstrumentationRegistry.getArguments()
    private val mangaId = args.getString("benchMangaId")?.toLongOrNull()
    private val chapterId = args.getString("benchChapterId")?.toLongOrNull()

    @Test
    fun readerFlingScroll() {
        assumeTrue(
            "benchMangaId/benchChapterId instrumentation args not provided - test skipped",
            mangaId != null && chapterId != null,
        )
        val manga = requireNotNull(mangaId)
        val chapter = requireNotNull(chapterId)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait(
                Intent()
                    .setClassName(TARGET_PACKAGE, READER_ACTIVITY)
                    .putExtra("manga", manga)
                    .putExtra("chapter", chapter),
            )
            device.waitForIdle()
            // Let the chapter load and the first pages decode so the flings measure scrolling,
            // not initial page fetch.
            Thread.sleep(4_000)

            val width = device.displayWidth
            val height = device.displayHeight
            // Forward flings (content up), then backward flings - the backward pass is where
            // the webtoon extra-layout-space/re-decode behaviour shows up.
            repeat(6) {
                device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
                Thread.sleep(600)
            }
            repeat(6) {
                device.swipe(width / 2, height / 4, width / 2, height * 3 / 4, 20)
                Thread.sleep(600)
            }
        }
    }
}

package tachiyomi.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.tadami.aurora.benchmark"

/**
 * Measures frame timing while fling-scrolling the home screen (library by default).
 * Data-dependent: with an empty library it measures the empty-state scroll, which is still
 * usable as a stable relative baseline, but populate the library for meaningful numbers.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class LibraryScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun libraryFlingScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2_000)

        val width = device.displayWidth
        val height = device.displayHeight
        repeat(5) {
            device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 30)
            Thread.sleep(450)
        }
        repeat(5) {
            device.swipe(width / 2, height / 3, width / 2, height * 3 / 4, 30)
            Thread.sleep(450)
        }
    }
}

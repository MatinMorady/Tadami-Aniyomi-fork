package eu.kanade.tachiyomi.network.interceptor

import android.app.Activity
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

class CurrentActivityTrackerTest {

    @Test
    fun `tracks resumed activity and clears it on pause`() {
        val activity = mockk<Activity>(relaxed = true)

        CurrentActivityTracker.onActivityResumed(activity)
        CurrentActivityTracker.current shouldBe activity

        CurrentActivityTracker.onActivityPaused(activity)
        CurrentActivityTracker.current shouldBe null
    }

    @Test
    fun `pause of another activity keeps the resumed one`() {
        val first = mockk<Activity>(relaxed = true)
        val second = mockk<Activity>(relaxed = true)

        CurrentActivityTracker.onActivityResumed(first)
        CurrentActivityTracker.onActivityPaused(second)
        CurrentActivityTracker.current shouldBe first

        CurrentActivityTracker.onActivityPaused(first)
        CurrentActivityTracker.current shouldBe null
    }

    @Test
    fun `destroyed activity never lingers`() {
        val activity = mockk<Activity>(relaxed = true)

        CurrentActivityTracker.onActivityResumed(activity)
        CurrentActivityTracker.onActivityDestroyed(activity)
        CurrentActivityTracker.current shouldBe null
    }
}

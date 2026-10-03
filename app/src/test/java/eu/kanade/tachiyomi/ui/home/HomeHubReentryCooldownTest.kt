package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.Test

class HomeHubReentryCooldownTest {

    private val now = 1_000_000_000L

    @Test
    fun `first reentry rotates`() {
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = 0L) shouldBe true
    }

    @Test
    fun `rapid tab flip inside cooldown does not rotate`() {
        // Флип вкладок: 0.5с, 2с, 10с, 59с от последней ротации — метить пул нельзя.
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = now - 500L) shouldBe false
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = now - 2_000L) shouldBe false
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = now - 10_000L) shouldBe false
        shouldAdvanceDiscoveryTeaserOnReentry(
            now,
            lastReentryTimeMs = now - DISCOVERY_REENTRY_COOLDOWN_MS + 1L,
        ) shouldBe false
    }

    @Test
    fun `return after cooldown rotates`() {
        shouldAdvanceDiscoveryTeaserOnReentry(
            now,
            lastReentryTimeMs = now - DISCOVERY_REENTRY_COOLDOWN_MS,
        ) shouldBe true
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = now - 5 * 60_000L) shouldBe true
    }

    @Test
    fun `custom cooldown is respected`() {
        shouldAdvanceDiscoveryTeaserOnReentry(now, now - 2_000L, cooldownMs = 1_000L) shouldBe true
        shouldAdvanceDiscoveryTeaserOnReentry(now, now - 500L, cooldownMs = 1_000L) shouldBe false
    }

    @Test
    fun `clock skew backwards does not rotate`() {
        // lastReentry в «будущем» (перевод часов): консервативно не ротируем.
        shouldAdvanceDiscoveryTeaserOnReentry(now, lastReentryTimeMs = now + 60_000L) shouldBe false
    }
}

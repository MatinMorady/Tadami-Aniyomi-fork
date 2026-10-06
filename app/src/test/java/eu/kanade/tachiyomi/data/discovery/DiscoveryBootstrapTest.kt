package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoveryBootstrapTest {

    private val now = 1_000_000_000L

    @Test
    fun `first launch with no cache bootstraps immediately`() {
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = null,
            lastBootstrapAttemptAt = 0L,
            now = now,
        ) shouldBe true
    }

    @Test
    fun `existing cache never bootstraps`() {
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = now - 1000L,
            lastBootstrapAttemptAt = 0L,
            now = now,
        ) shouldBe false
    }

    @Test
    fun `failed attempt blocks re-bootstrap within retry window`() {
        // Попытка 5 минут назад (окно 6 часов): шторма нет.
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = null,
            lastBootstrapAttemptAt = now - 5 * 60_000L,
            now = now,
        ) shouldBe false
    }

    @Test
    fun `failed attempt allows re-bootstrap after retry window`() {
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = null,
            lastBootstrapAttemptAt = now - DISCOVERY_BOOTSTRAP_RETRY_MS - 1L,
            now = now,
        ) shouldBe true
    }

    @Test
    fun `clock skew backwards is treated as retry-eligible`() {
        // lastAttempt в «будущем» относительно now (перевод часов): разрешаем попытку,
        // иначе гвард залипает до догоняющего now.
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = null,
            lastBootstrapAttemptAt = now + 3600_000L,
            now = now,
        ) shouldBe true
    }

    @Test
    fun `custom retry window is respected`() {
        shouldBootstrapDiscoveryFeed(
            lastUpdatedAt = null,
            lastBootstrapAttemptAt = now - 1000L,
            now = now,
            retryMs = 500L,
        ) shouldBe true
    }
}

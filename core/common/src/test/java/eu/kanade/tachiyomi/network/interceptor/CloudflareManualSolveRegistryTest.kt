package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CloudflareManualSolveRegistryTest {

    @BeforeEach
    fun reset() {
        CloudflareManualSolveRegistry.clearAll()
    }

    @Test
    fun `failed solve marks host pending until cleared`() {
        CloudflareManualSolveRegistry.isPending("www.novelupdates.com") shouldBe false

        CloudflareManualSolveRegistry.request("www.novelupdates.com")
        CloudflareManualSolveRegistry.isPending("www.novelupdates.com") shouldBe true

        CloudflareManualSolveRegistry.clearAll()
        CloudflareManualSolveRegistry.isPending("www.novelupdates.com") shouldBe false
    }

    @Test
    fun `hosts are tracked independently`() {
        CloudflareManualSolveRegistry.request("a.example")

        CloudflareManualSolveRegistry.isPending("a.example") shouldBe true
        CloudflareManualSolveRegistry.isPending("b.example") shouldBe false
    }
}

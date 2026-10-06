package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Contract for [applyThenConsume] - the ordering that fixes the lost "switch to the Manga
 * section" route intent in the SHORTCUT_MANGA flow (v0.62.8: pressing "Back to manga" after
 * the finale plate and then leaving the manga page landed on the stale Anime section because
 * the consume-first CAS had already swallowed the intent inside a composition that the
 * MangaScreen push killed mid-apply).
 */
class LibraryRouteIntentConsumptionTest {

    /** The pre-fix consume-first ordering, reproduced locally to pin the regression. */
    private suspend fun <T> consumeFirstThenApply(
        pendingIntents: MutableStateFlow<T?>,
        intent: T,
        apply: suspend (T) -> Unit,
    ) {
        if (!pendingIntents.compareAndSet(intent, null)) return
        apply(intent)
    }

    @Test
    fun `consume-first loses the intent when the composition dies mid-apply`() = runTest {
        val pending = MutableStateFlow<String?>("switch-to-manga")
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            consumeFirstThenApply(pending, "switch-to-manga") { awaitCancellation() }
        }
        job.cancel()
        // Consumed before the cancelled apply - the next composition never re-applies it:
        // the user lands back on the stale section. This is the pinned old behavior.
        pending.value shouldBe null
    }

    @Test
    fun `apply-then-consume keeps the intent when the composition dies mid-apply`() = runTest {
        val pending = MutableStateFlow<String?>("switch-to-manga")
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            applyThenConsume(pending, "switch-to-manga") { awaitCancellation() }
        }
        job.cancel()
        // Not consumed: the next composition re-applies and the section switch is not lost.
        pending.value shouldBe "switch-to-manga"
    }

    @Test
    fun `apply-then-consume clears exactly the applied intent`() = runTest {
        val pending = MutableStateFlow<String?>("switch-to-manga")
        var applied: String? = null
        applyThenConsume(pending, "switch-to-manga") { applied = it }
        applied shouldBe "switch-to-manga"
        pending.value shouldBe null
    }

    @Test
    fun `apply-then-consume does not clobber an intent that arrived while applying`() = runTest {
        val pending = MutableStateFlow<String?>("first")
        applyThenConsume(pending, "first") { pending.value = "second" }
        pending.value shouldBe "second"
    }
}

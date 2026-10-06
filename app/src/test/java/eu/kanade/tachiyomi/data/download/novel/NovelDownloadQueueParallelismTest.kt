package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class NovelDownloadQueueParallelismTest {

    @Test
    fun `runtime state tracks several concurrent active downloads`() {
        runBlocking {
            val runtime = NovelDownloadQueueRuntimeState()
            val started1 = CompletableDeferred<Unit>()
            val started2 = CompletableDeferred<Unit>()
            val gate1 = CompletableDeferred<Unit>()
            val gate2 = CompletableDeferred<Unit>()
            val job1 = launch {
                started1.complete(Unit)
                gate1.await()
            }
            val job2 = launch {
                started2.complete(Unit)
                gate2.await()
            }
            started1.await()
            started2.await()

            runtime.registerActiveDownload(taskId = 1L, job = job1)
            runtime.registerActiveDownload(taskId = 2L, job = job2)

            runtime.activeDownloadCount() shouldBe 2

            // Cancel addresses only the matching task
            runtime.cancelActiveDownload(taskId = 1L) shouldBe true
            job1.cancelAndJoin()
            runtime.cancelActiveDownload(taskId = 2L) shouldBe true
            job2.cancelAndJoin()

            // Clearing removes the job from the active set
            runtime.activeDownloadCount() shouldBe 2
            runtime.clearActiveDownload(taskId = 2L, job = job2)
            runtime.activeDownloadCount() shouldBe 1
            runtime.clearActiveDownload(taskId = 1L, job = job1)
            runtime.activeDownloadCount() shouldBe 0
        }
    }

    @Test
    fun `cancel of unknown task is a no-op`() {
        runBlocking {
            val runtime = NovelDownloadQueueRuntimeState()
            val gate = CompletableDeferred<Unit>()
            val job = launch { gate.await() }

            runtime.registerActiveDownload(taskId = 5L, job = job)
            runtime.cancelActiveDownload(taskId = 99L) shouldBe false
            runtime.cancelActiveDownload(taskId = 5L) shouldBe true
            job.cancelAndJoin()
        }
    }
}

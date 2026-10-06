package eu.kanade.tachiyomi.data.download.manga

import android.app.Application
import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.nio.file.Path

/**
 * Issue #143: downloads moved from another app land in a folder whose (title-derived) name no
 * longer matches the DB after a refresh/sanitization drift, so the download cache resolved zero
 * directories -> "Downloaded" filter and badges ignored every chapter. The mangaId suffix
 * " [id]" at the end of the folder name is a stable anchor for such folders.
 */
class MangaDownloadCacheDirMatchingTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `count matches a folder whose title drifted but kept the id suffix`() {
        val cache = createCache()
        val manga = Manga.create().copy(id = 5L, source = 10L, title = "New Title")
        val sourceDir = File(tempDir.resolve("downloads").toFile(), sourceDirName())
        // On-disk folder created under an older title, e.g. moved from another app.
        File(sourceDir, "Old Title [5]/Ch 1").mkdirs()
        File(sourceDir, "Old Title [5]/Ch 2").mkdirs()

        awaitRenewal(cache)

        cache.getDownloadCount(manga) shouldBe 2
    }

    @Test
    fun `exact scoped and legacy names still match`() {
        val cache = createCache()
        val manga = Manga.create().copy(id = 5L, source = 10L, title = "New Title")
        val sourceDir = File(tempDir.resolve("downloads").toFile(), sourceDirName())
        File(sourceDir, "New Title [5]/Ch 1").mkdirs()
        File(sourceDir, "New Title/Ch 2").mkdirs()

        awaitRenewal(cache)

        cache.getDownloadCount(manga) shouldBe 2
    }

    @Test
    fun `folders of other manga ids are never matched`() {
        val cache = createCache()
        val manga = Manga.create().copy(id = 5L, source = 10L, title = "New Title")
        val sourceDir = File(tempDir.resolve("downloads").toFile(), sourceDirName())
        File(sourceDir, "Other Entry [6]/Ch 1").mkdirs()

        awaitRenewal(cache)

        cache.getDownloadCount(manga) shouldBe 0
    }

    /** renewCache() runs on Dispatchers.IO; poll until the snapshot swaps in (bounded). */
    private fun awaitRenewal(cache: MangaDownloadCache) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            if (cache.getTotalDownloadCount() > 0) return
        }
    }

    private fun sourceDirName(): String = "Web Central (EN)"

    private fun createCache(): MangaDownloadCache {
        Injekt.addSingleton<Application>(mockk(relaxed = true))
        val root = tempDir.resolve("downloads").toFile().apply { mkdirs() }
        val storageManager = mockk<StorageManager> {
            every { getDownloadsDirectory() } returns fakeUniFile(root)
            every { changes } returns MutableSharedFlow()
        }
        val source = mockk<HttpSource>()
        // Source dir names derive from toString() = "$name (${lang.uppercase()})".
        every { source.id } returns 10L
        every { source.name } returns "Web Central"
        every { source.lang } returns "en"
        every { source.toString() } returns sourceDirName()
        val sourceManager = mockk<MangaSourceManager> {
            every { getOrStub(any()) } returns source
            every { getOnlineSources() } returns listOf(source)
            every { getStubSources() } returns emptyList()
            every { isInitialized } returns MutableStateFlow(true)
        }
        val extensionManager = mockk<MangaExtensionManager> {
            every { isInitialized } returns MutableStateFlow(true)
        }
        val context = mockk<Context> {
            every { cacheDir } returns tempDir.resolve("cache").toFile().apply { mkdirs() }
        }
        return MangaDownloadCache(
            context = context,
            provider = MangaDownloadProvider(
                context = context,
                storageManager = storageManager,
            ),
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            storageManager = storageManager,
        )
    }

    private fun fakeUniFile(file: File): UniFile {
        val normalized = file.absoluteFile
        return mockk(relaxed = true) {
            every { getName() } returns normalized.name
            every { getFilePath() } returns normalized.absolutePath
            every { isDirectory() } answers { normalized.isDirectory }
            every { isFile() } answers { normalized.isFile }
            every { exists() } answers { normalized.exists() }
            every { listFiles() } answers {
                normalized.listFiles()?.map { fakeUniFile(it) }?.toTypedArray() ?: emptyArray()
            }
            every { findFile(any()) } answers {
                val child = File(normalized, firstArg<String>())
                if (child.exists()) fakeUniFile(child) else null
            }
            every { createDirectory(any()) } answers {
                val child = File(normalized, firstArg<String>())
                child.mkdirs()
                fakeUniFile(child)
            }
        }
    }
}

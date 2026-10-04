package eu.kanade.tachiyomi.data.download.manga

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.nio.file.Path

/**
 * DECISION-6/7 (C-M1/C-M3): scoped id-suffixed download names and the legacy lookup variants.
 * Red before the fix: "A:B" and "A_B" sanitized to the SAME chapter dir name, so two chapter ids
 * collided into one directory (the second rename failed -> eternal ERROR + orphan _tmp), and
 * same-title manga of one source shared a directory (cross-delete destroyed the neighbor).
 */
class MangaDownloadProviderNamingTest {

    @field:TempDir
    lateinit var tempDir: Path

    private val provider = MangaDownloadProvider(
        context = mockk(relaxed = true),
        storageManager = mockk(relaxed = true),
    )

    @Test
    fun `scoped chapter names disambiguate sanitization collisions`() {
        val first = provider.getChapterDirName("A:B", null, chapterId = 1L)
        val second = provider.getChapterDirName("A_B", null, chapterId = 2L)
        first shouldBe "A_B [1]"
        second shouldBe "A_B [2]"
    }

    @Test
    fun `duplicate chapter names of one manga get distinct dirs`() {
        val a = provider.getChapterDirName("Chapter 10", "Scans", chapterId = 5L)
        val b = provider.getChapterDirName("Chapter 10", "Scans", chapterId = 6L)
        a shouldBe "Scans_Chapter 10 [5]"
        b shouldBe "Scans_Chapter 10 [6]"
    }

    @Test
    fun `valid chapter names include scoped, legacy and baseline variants`() {
        val names = provider.getValidChapterDirNames("Ch 1", "Scans", chapterId = 9L)
        // Scoped (new downloads)
        names shouldContain "Scans_Ch 1 [9]"
        names shouldContain "Scans_Ch 1 [9].cbz"
        // Legacy pre-DECISION-6 (read-only compatibility)
        names shouldContain "Scans_Ch 1"
        names shouldContain "Scans_Ch 1.cbz"
        // DECISION-7: pre-0.9.2 scanlator-less names removed by the upstream cleanup
        names shouldContain "Ch 1"
        names shouldContain "Ch 1.cbz"
    }

    @Test
    fun `blank scanlator exposes the historical underscore form`() {
        val names = provider.getValidChapterDirNames("Ch 2", "", chapterId = 3L)
        names shouldContain "_Ch 2"
        names shouldContain "_Ch 2.cbz"
        names shouldContain "Ch 2 [3]"
        // The scanlator-less variant coincides with the legacy name for blank scanlators.
        names shouldContain "Ch 2"
    }

    @Test
    fun `scoped manga dir name carries the id and legacy stays title-only`() {
        provider.getMangaDirName("One Title", 5L) shouldBe "One Title [5]"
        provider.getLegacyMangaDirName("One Title") shouldBe "One Title"
        provider.getMangaDirName("One Title") shouldBe "One Title"
    }

    @Test
    fun `getMangaDir reuses an existing legacy title-only folder instead of creating a scoped duplicate`() {
        val root = tempDir.resolve("downloads").toFile()
        val storageManager = mockk<StorageManager>()
        every { storageManager.getDownloadsDirectory() } returns fakeUniFile(root)
        val providerWithDir = MangaDownloadProvider(
            context = mockk(relaxed = true),
            storageManager = storageManager,
        )
        val source = mockk<MangaSource>()
        every { source.toString() } returns "Web Central (EN)"

        // Simulates downloads moved from another app: legacy title-only folder on disk.
        val legacyDir = File(root, "Web Central (EN)/One Title").apply { mkdirs() }

        val dir = providerWithDir.getMangaDir("One Title", 5L, source)

        // New downloads must land in the existing folder, not a "One Title [5]" duplicate.
        dir.filePath shouldBe legacyDir.absolutePath
        File(root, "Web Central (EN)").listFiles()!!.map { it.name } shouldBe listOf("One Title")
    }

    @Test
    fun `getMangaDir reuses an existing scoped folder`() {
        val root = tempDir.resolve("downloads").toFile()
        val storageManager = mockk<StorageManager>()
        every { storageManager.getDownloadsDirectory() } returns fakeUniFile(root)
        val providerWithDir = MangaDownloadProvider(
            context = mockk(relaxed = true),
            storageManager = storageManager,
        )
        val source = mockk<MangaSource>()
        every { source.toString() } returns "Web Central (EN)"

        val existingDir = File(root, "Web Central (EN)/One Title [5]").apply { mkdirs() }

        val dir = providerWithDir.getMangaDir("One Title", 5L, source)

        // The exact scoped folder is reused, not re-created or duplicated.
        dir.filePath shouldBe existingDir.absolutePath
    }
}

/** Minimal local-file UniFile fake (same contract the novel-side tests rely on). */
private fun fakeUniFile(file: File): UniFile {
    val normalized = file.absoluteFile
    return mockk(relaxed = true) {
        every { getName() } returns normalized.name
        every { getFilePath() } returns normalized.absolutePath
        every { isDirectory() } answers { normalized.isDirectory }
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

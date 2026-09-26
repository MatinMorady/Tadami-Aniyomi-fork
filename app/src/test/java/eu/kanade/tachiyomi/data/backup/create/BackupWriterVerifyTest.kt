package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.BackupContentSummary
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

/**
 * Behavior of the staged-write verification that replaced the full decode.
 *
 * The old implementation decompressed the staged file into a second payload copy and decoded it
 * into a complete object graph; on a 256 MB heap that died with OutOfMemoryError exactly on large
 * libraries. These tests pin the replacement: byte identity via streaming digests, plus origin and
 * content counts read from the wire — all three must still reject a bad staged artifact.
 */
class BackupWriterVerifyTest {

    @Test
    fun `staged file holding exactly the payload passes`(@TempDir dir: File) {
        val payload = nativePayload(manga = 2, categories = 1)
        val staged = gzip(dir, payload)

        verifyStagedBackup(
            staged = staged,
            payload = payload,
            expected = BackupContentSummary(mangaCount = 2, categoriesCount = 1),
            expectedOrigin = BackupOrigin.TADAMI,
        )
    }

    @Test
    fun `staged file that does not decompress to the payload is rejected`(@TempDir dir: File) {
        val payload = nativePayload(manga = 2, categories = 1)
        // Staged holds different bytes: the digest tie must fire before any content check.
        val staged = gzip(dir, nativePayload(manga = 3, categories = 1))

        assertThrows<IOException> {
            verifyStagedBackup(
                staged = staged,
                payload = payload,
                expected = BackupContentSummary(mangaCount = 2, categoriesCount = 1),
                expectedOrigin = BackupOrigin.TADAMI,
            )
        }
    }

    @Test
    fun `payload whose counts do not match the expectation is rejected`(@TempDir dir: File) {
        val payload = nativePayload(manga = 2, categories = 1)
        val staged = gzip(dir, payload)

        assertThrows<IOException> {
            verifyStagedBackup(
                staged = staged,
                payload = payload,
                expected = BackupContentSummary(mangaCount = 5, categoriesCount = 1),
                expectedOrigin = BackupOrigin.TADAMI,
            )
        }
    }

    @Test
    fun `payload of a different origin is rejected`(@TempDir dir: File) {
        val payload = nativePayload(manga = 2, categories = 1)
        val staged = gzip(dir, payload)

        assertThrows<IOException> {
            verifyStagedBackup(
                staged = staged,
                payload = payload,
                expected = BackupContentSummary(mangaCount = 2, categoriesCount = 1),
                expectedOrigin = BackupOrigin.TADAMI_SISTER,
            )
        }
    }

    private fun nativePayload(manga: Int, categories: Int): ByteArray {
        val backup = Backup(
            backupManga = (1..manga).map { BackupManga(source = it.toLong(), url = "u$it", title = "t$it") },
            backupCategories = (1..categories).map { BackupCategory(name = "c$it") },
            isLegacy = false,
        )
        return ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
    }

    private fun gzip(dir: File, payload: ByteArray): File {
        val file = File(dir, "staged.tachibk")
        FileOutputStream(file).use { out -> GZIPOutputStream(out).use { it.write(payload) } }
        return file
    }
}

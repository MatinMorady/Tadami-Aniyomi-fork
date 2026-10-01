package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.toMihonBackup
import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

/**
 * Pins the wire-level inspection that [eu.kanade.tachiyomi.data.backup.create.verifyStagedBackup]
 * relies on: origin detection and content counts read straight from the payload, without decoding
 * it into an object graph.
 *
 * These are the two properties the staged writer used to prove with a full decode — the decode is
 * what exhausted the 256 MB heap on large libraries (OutOfMemoryError in verify_staged), so the
 * wire-level equivalents must stay exact.
 */
class BackupWireSummaryTest {

    @Test
    fun `native payload reports the same counts as the in-memory model`() {
        val backup = Backup(
            backupManga = listOf(manga(1), manga(2)),
            backupAnime = listOf(anime(3)),
            backupNovel = listOf(novel(4), novel(5), novel(6)),
            backupCategories = listOf(category("manga-cat")),
            backupAnimeCategories = listOf(category("anime-cat")),
            backupNovelCategories = listOf(category("novel-a"), category("novel-b")),
            isLegacy = false,
        )
        val payload = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

        BackupDetector.contentSummary(payload) shouldBe backup.contentSummary()
        BackupDetector.contentSummary(payload) shouldBe BackupContentSummary(
            mangaCount = 2,
            animeCount = 1,
            novelCount = 3,
            categoriesCount = 4,
        )
    }

    @Test
    fun `native payload carrying anime and novel is detected as tadami`() {
        // Field 500 (isLegacy=false) is the native marker; anime/novel live at 501/508, NOT at the
        // legacy 3/5 — a native payload must never be mistaken for a legacy Aniyomi one.
        val backup = Backup(
            backupManga = listOf(manga(1)),
            backupAnime = listOf(anime(2)),
            backupNovel = listOf(novel(3)),
            isLegacy = false,
        )
        val payload = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

        BackupDetector.detectOrigin(payload) shouldBe BackupOrigin.TADAMI
    }

    @Test
    fun `sister payload flattens novels into manga and is detected as sister`() {
        val backup = Backup(
            backupManga = listOf(manga(1)),
            backupNovel = listOf(novel(2), novel(3)),
            backupCategories = listOf(category("cat")),
            isLegacy = false,
        )
        val payload = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup.toMihonBackup())

        BackupDetector.detectOrigin(payload) shouldBe BackupOrigin.TADAMI_SISTER
        // Sister exports carry novels inside field 1, so anime/novel read zero — exactly the
        // adjusted summary BackupCreator hands to the writer for that format.
        BackupDetector.contentSummary(payload) shouldBe BackupContentSummary(
            mangaCount = 3,
            animeCount = 0,
            novelCount = 0,
            categoriesCount = 1,
        )
    }

    @Test
    fun `sister payload carrying anime reports it on the wire and stays sister`() {
        val backup = Backup(
            backupManga = listOf(manga(1)),
            backupAnime = listOf(anime(2), anime(3)),
            backupNovel = listOf(novel(4)),
            backupCategories = listOf(category("cat")),
            backupAnimeCategories = listOf(category("anime-cat")),
            isLegacy = false,
        )
        val payload = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup.toMihonBackup())

        BackupDetector.detectOrigin(payload) shouldBe BackupOrigin.TADAMI_SISTER
        BackupDetector.contentSummary(payload) shouldBe BackupContentSummary(
            mangaCount = 2,
            animeCount = 2,
            novelCount = 0,
            categoriesCount = 2,
        )
    }

    @Test
    fun `legacy payload with anime at field 3 is detected as legacy aniyomi`() {
        val legacy = LegacyBackup(
            backupManga = listOf(manga(1)),
            backupAnime = listOf(anime(2)),
        )
        val payload = ProtoBuf.encodeToByteArray(LegacyBackup.serializer(), legacy)

        BackupDetector.detectOrigin(payload) shouldBe BackupOrigin.LEGACY_ANIYOMI
    }

    private fun manga(id: Long) = BackupManga(source = id, url = "manga/$id", title = "Manga $id")
    private fun anime(id: Long) = BackupAnime(source = id, url = "anime/$id", title = "Anime $id")
    private fun novel(id: Long) = BackupNovel(source = id, url = "novel/$id", title = "Novel $id")
    private fun category(name: String) = BackupCategory(name = name)
}

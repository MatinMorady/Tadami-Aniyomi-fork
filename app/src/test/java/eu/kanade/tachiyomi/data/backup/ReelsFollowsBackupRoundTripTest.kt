package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.toBackupReelsFollow
import eu.kanade.tachiyomi.data.backup.models.toReelsFollow
import eu.kanade.tachiyomi.data.backup.restore.restorers.ReelsFollowsRestorer
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.domain.reels.anime.repository.ReelsFollowRepository
import java.util.Date

class ReelsFollowsBackupRoundTripTest {

    private val follow = ReelsFollow(
        sourceId = 101L,
        creator = "alice",
        addedAt = Date(1_700_000_000_000L),
    )

    @Test
    fun `reels follow survives proto round trip`() {
        val backup = Backup(backupReelsFollows = listOf(follow.toBackupReelsFollow()))

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        decoded.backupReelsFollows.size shouldBe 1
        decoded.backupReelsFollows.first().toReelsFollow() shouldBe follow
    }

    @Test
    fun `empty backup decodes without reels follows`() {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.backupReelsFollows shouldBe emptyList()
    }

    @Test
    fun `mapper preserves all fields`() {
        follow.toBackupReelsFollow().toReelsFollow() shouldBe follow
    }

    @Test
    fun `restore maps a zero addedAt to now instead of the epoch`() {
        val backupFollow = follow.copy(creator = "no-ts").toBackupReelsFollow().copy(addedAt = 0L)

        backupFollow.toReelsFollow().addedAt.time shouldBeGreaterThan 1_700_000_000_000L
    }

    @Test
    fun `restore never overwrites same-key follows - local state is authoritative`() {
        val repo = RecordingFollowsRepository()
        val restorer = ReelsFollowsRestorer(repo)
        repo.follows[101L to "bob"] = follow.copy(creator = "bob")

        runBlocking {
            restorer.restoreReelsFollows(listOf(follow.copy(creator = "bob").toBackupReelsFollow()))
        }

        // Local unfollow must not resurrect: the row is skipped, not replaced.
        repo.follows.keys shouldBe setOf(101L to "bob")
        repo.follows[101L to "bob"]!!.addedAt shouldBe follow.addedAt
    }

    @Test
    fun `restore inserts follows missing locally`() {
        val repo = RecordingFollowsRepository()
        val restorer = ReelsFollowsRestorer(repo)

        runBlocking {
            restorer.restoreReelsFollows(listOf(follow.toBackupReelsFollow()))
        }

        repo.follows.keys shouldBe setOf(101L to "alice")
    }

    @Test
    fun `restore keeps follows of sources that are not installed yet`() {
        // Extension restore only launches the SYSTEM installer (manual confirmation, finishes
        // after the restore job), so the source manager is empty here — filtering on it would
        // silently drop every follow on a fresh install. Rows survive unconditionally; follows
        // of still-missing sources stay inert until the extension is installed.
        val repo = RecordingFollowsRepository()
        val restorer = ReelsFollowsRestorer(repo)

        runBlocking {
            restorer.restoreReelsFollows(listOf(follow.copy(sourceId = 999L).toBackupReelsFollow()))
        }

        repo.follows.keys shouldBe setOf(999L to "alice")
    }
}

private class RecordingFollowsRepository : ReelsFollowRepository {
    val follows = mutableMapOf<Pair<Long, String>, ReelsFollow>()

    override fun subscribeAll(): Flow<List<ReelsFollow>> = MutableStateFlow(follows.values.toList())

    override suspend fun getAll(): List<ReelsFollow> = follows.values.toList()

    override suspend fun getBySource(sourceId: Long): List<ReelsFollow> =
        follows.values.filter { it.sourceId == sourceId }

    override suspend fun getCreatorsBySource(sourceId: Long): List<String> =
        follows.values.filter { it.sourceId == sourceId }.map { it.creator }

    override suspend fun insert(follow: ReelsFollow) {
        follows[follow.sourceId to follow.creator] = follow
    }

    override suspend fun delete(sourceId: Long, creator: String) {
        follows.remove(sourceId to creator)
    }
}

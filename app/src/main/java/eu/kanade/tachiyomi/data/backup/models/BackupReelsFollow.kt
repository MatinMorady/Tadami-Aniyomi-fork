package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.reels.anime.model.ReelsFollow
import java.util.Date

@Serializable
data class BackupReelsFollow(
    @ProtoNumber(1) val sourceId: Long,
    @ProtoNumber(2) val creator: String,
    @ProtoNumber(3) val addedAt: Long = 0L,
)

fun ReelsFollow.toBackupReelsFollow(): BackupReelsFollow {
    return BackupReelsFollow(sourceId = sourceId, creator = creator, addedAt = addedAt.time)
}

fun BackupReelsFollow.toReelsFollow(): ReelsFollow {
    return ReelsFollow(
        sourceId = sourceId,
        creator = creator,
        // Proto3 scalars cannot distinguish "absent" from 0: a missing timestamp must not
        // pin the restored follow to epoch zero (same rule as the favorites model).
        addedAt = if (addedAt == 0L) Date() else Date(addedAt),
    )
}

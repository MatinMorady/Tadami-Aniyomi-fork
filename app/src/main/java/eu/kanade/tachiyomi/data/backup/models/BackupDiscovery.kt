package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType

/**
 * Пользовательские негативные сигналы «Для тебя» в backup (A-цикл):
 * скрытые тайтлы и теговый блэклист. Кэш подборок (discovery_suggestions)
 * не бэкапится — он регенерируем.
 */
@Serializable
data class BackupDiscoveryHidden(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val cleanTitle: String,
    @ProtoNumber(3) val hiddenAt: Long = 0L,
)

@Serializable
data class BackupDiscoveryTag(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val tag: String,
    @ProtoNumber(3) val addedAt: Long = 0L,
)

/** Taste Learning Engine: сигнал-лог вкусов в backup (переживает переустановку). */
@Serializable
data class BackupDiscoverySignal(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val cleanTitle: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val signal: String,
    @ProtoNumber(5) val genres: String = "",
    @ProtoNumber(6) val provider: String? = null,
    @ProtoNumber(7) val sourceKey: String? = null,
    @ProtoNumber(8) val createdAt: Long = 0L,
)

fun DiscoveryHiddenEntry.toBackupDiscoveryHidden(mediaTypeKey: String) = BackupDiscoveryHidden(
    mediaType = mediaTypeKey,
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun DiscoveryBlacklistEntry.toBackupDiscoveryTag(mediaTypeKey: String) = BackupDiscoveryTag(
    mediaType = mediaTypeKey,
    tag = tag,
    addedAt = addedAt,
)

fun DiscoverySignal.toBackupDiscoverySignal() = BackupDiscoverySignal(
    mediaType = mediaType.key,
    cleanTitle = cleanTitle,
    title = title,
    signal = signalType.key,
    genres = genres.joinToString(","),
    provider = provider,
    sourceKey = sourceKey,
    createdAt = createdAt,
)

fun BackupDiscoverySignal.toDomainSignal(): DiscoverySignal? {
    val media = DiscoveryMediaType.fromKey(mediaType) ?: return null
    val type = DiscoverySignalType.fromKey(signal) ?: return null
    return DiscoverySignal(
        mediaType = media,
        cleanTitle = cleanTitle,
        title = title.ifBlank { cleanTitle },
        signalType = type,
        genres = genres.splitToSequence(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toList(),
        provider = provider,
        sourceKey = sourceKey,
        createdAt = createdAt,
    )
}

fun BackupDiscoveryHidden.toDomainEntry() = DiscoveryHiddenEntry(
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun BackupDiscoveryTag.toDomainEntry() = DiscoveryBlacklistEntry(
    tag = tag,
    addedAt = addedAt,
)

package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.lnreader.LNReaderBackup
import eu.kanade.tachiyomi.data.backup.models.TadamiSisterManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Try to guess if the backup is an old aniyomi backup.
 *
 * Returns true if it's (probably) an old aniyomi backup, or false if it's a mihon backup
 * or a new aniyomi backup.
 */
object BackupDetector {
    @Serializable
    data class BackupDetector(
        @ProtoNumber(103) val backupAnimeSources: List<DetectAnimeSource> = emptyList(),
        @ProtoNumber(500) val isLegacy: Boolean = true,
    ) {
        @Serializable
        data class DetectAnimeSource(
            @ProtoNumber(1) val name: String = "",
            @ProtoNumber(2) val sourceId: Long,
        )
    }

    fun isLegacyBackup(bytes: ByteArray): Boolean {
        return try {
            val fields = topLevelFieldNumbers(bytes)
            // Legacy Aniyomi/Tadami stores anime/novel at top-level fields 3 and 5.
            if (LEGACY_ANIME_FIELD in fields || LEGACY_NOVEL_FIELD in fields) {
                return true
            }
            val detect = ProtoBuf.decodeFromByteArray(BackupDetector.serializer(), bytes)
            detect.isLegacy && detect.backupAnimeSources.isNotEmpty()
        } catch (_: SerializationException) {
            false
        }
    }

    /** True when the wire format still carries legacy anime/novel payload fields. */
    fun hasLegacyPayloadFields(bytes: ByteArray): Boolean {
        return try {
            val fields = topLevelFieldNumbers(bytes)
            LEGACY_ANIME_FIELD in fields || LEGACY_NOVEL_FIELD in fields
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Positively identify a Mihon / Tachiyomi(-derived) backup (as opposed to a
     * native Aniyomi/Tadami backup).
     *
     * Native Aniyomi/Tadami backups always carry at least one "native marker" field
     * at the top level: legacy backupAnime(3), backupAnimeCategories(4),
     * legacy backupNovel(5), backupNovelCategories(6), or one of Tadami's
     * unambiguous native fields. BackupCreator always writes isLegacy=false at
     * field 500. Forks such as SY and Komikku also use fields in the 600+ range,
     * so treating every field >= 500 as native would reject compatible backups.
     *
     * So a backup with no native marker but recognizable Mihon content is a Mihon
     * backup. Must be checked AFTER [isLegacyBackup].
     */
    fun isMihonBackup(bytes: ByteArray): Boolean {
        return detectOrigin(bytes) in MIHON_DERIVED_ORIGINS
    }

    /**
     * Deterministically classify the backup from its content markers only.
     *
     * Installed extensions are never consulted: which app wrote a file is a property of the file.
     */
    fun detectOrigin(bytes: ByteArray): BackupOrigin {
        // LNReader does not use protobuf at all: it writes a JSON document (1.x) or a ZIP
        // container (2.x), so it is recognisable before any protobuf parsing is attempted.
        if (LNReaderBackup.isLNReaderContainer(bytes)) return BackupOrigin.LNREADER

        if (isLegacyBackup(bytes)) return BackupOrigin.LEGACY_ANIYOMI

        val fields = try {
            topLevelFieldNumbers(bytes)
        } catch (_: Exception) {
            return BackupOrigin.TADAMI
        }

        return when {
            fields.any { it in NATIVE_MARKER_FIELDS } -> BackupOrigin.TADAMI
            // Our own sister-app export: Mihon shaped, but carrying a manifest we can verify.
            hasConfirmedSisterManifest(bytes, fields) -> BackupOrigin.TADAMI_SISTER
            KOMIKKU_FEED_FIELD in fields -> BackupOrigin.KOMIKKU
            TACHIYOMI_SY_SAVED_SEARCH_FIELD in fields -> BackupOrigin.TACHIYOMI_SY
            fields.any { it in MIHON_CONTENT_FIELDS } -> BackupOrigin.MIHON
            else -> BackupOrigin.TADAMI
        }
    }

    /**
     * True only when field 20000 is present *and* decodes into a manifest with our signature and a
     * version this build understands. The presence of the field number alone proves nothing, since
     * any other app is free to use it.
     */
    private fun hasConfirmedSisterManifest(bytes: ByteArray, fields: Set<Int>): Boolean {
        if (TadamiSisterManifest.PROTO_FIELD !in fields) return false
        return try {
            // Decode only the manifest field's own bytes: pulling the whole MihonBackup into RAM
            // just to read field 20000 materializes the entire library next to the payload that is
            // being verified, which exhausted the heap on small-heap devices.
            val manifest = lastFieldPayload(bytes, TadamiSisterManifest.PROTO_FIELD) ?: return false
            ProtoBuf.decodeFromByteArray(TadamiSisterManifest.serializer(), manifest).isValid
        } catch (_: Exception) {
            false
        }
    }

    private val MIHON_CONTENT_FIELDS = setOf(1, 2, 101, 104, 105, 106)
    private val MIHON_DERIVED_ORIGINS =
        setOf(BackupOrigin.MIHON, BackupOrigin.TACHIYOMI_SY, BackupOrigin.KOMIKKU)
    private val NATIVE_MARKER_FIELDS =
        setOf(LEGACY_ANIME_FIELD, 4, LEGACY_NOVEL_FIELD, 6) +
            (500..510) +
            // 622 feeds, 623 reels favorites, 624/625 discovery, 626 reels follows.
            (620..626) +
            (650..652)

    private const val LEGACY_ANIME_FIELD = 3
    private const val LEGACY_NOVEL_FIELD = 5
    private const val TACHIYOMI_SY_SAVED_SEARCH_FIELD = 600
    private const val KOMIKKU_FEED_FIELD = 610

    // Native Backup schema (models/Backup.kt). Legacy Aniyomi/Tadami keeps anime/novel at 3/5,
    // the native format moved them to the 500 range; contentSummary counts the native numbers.
    private const val NATIVE_MANGA_FIELD = 1
    private const val NATIVE_CATEGORY_FIELD = 2
    private const val NATIVE_ANIME_FIELD = 501
    private const val NATIVE_ANIME_CATEGORY_FIELD = 502
    private const val NATIVE_NOVEL_FIELD = 508
    private const val NATIVE_NOVEL_CATEGORY_FIELD = 509

    /**
     * Per media type counts of a payload, read straight from the wire format without decoding it.
     *
     * Field numbers mirror the native [eu.kanade.tachiyomi.data.backup.models.Backup] schema:
     * 1 manga, 2 categories, 501 anime, 502 anime categories, 508 novel, 509 novel categories. A
     * sister export is Mihon shaped, so its flattened manga and novels both land on field 1 while
     * the anime/novel counters read zero — exactly what the writer expects for that format.
     *
     * A repeated message field occurs once per element, so counting top level occurrences is
     * equivalent to the decoded list sizes, at O(1) memory instead of a full object graph.
     */
    fun contentSummary(bytes: ByteArray): BackupContentSummary {
        val counts = topLevelFieldCounts(bytes)
        return BackupContentSummary(
            mangaCount = counts[NATIVE_MANGA_FIELD] ?: 0,
            animeCount = counts[NATIVE_ANIME_FIELD] ?: 0,
            novelCount = counts[NATIVE_NOVEL_FIELD] ?: 0,
            categoriesCount = (counts[NATIVE_CATEGORY_FIELD] ?: 0) +
                (counts[NATIVE_ANIME_CATEGORY_FIELD] ?: 0) +
                (counts[NATIVE_NOVEL_CATEGORY_FIELD] ?: 0),
        )
    }

    /**
     * Walk the top level of a protobuf message and collect the field numbers present.
     * Nested messages are skipped wholesale (not recursed into).
     */
    private fun topLevelFieldNumbers(bytes: ByteArray): Set<Int> {
        val fields = mutableSetOf<Int>()
        forEachTopLevelField(bytes) { number, _, _ -> fields += number }
        return fields
    }

    /** How many times each top level field occurs. A repeated message field occurs once per element. */
    private fun topLevelFieldCounts(bytes: ByteArray): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        forEachTopLevelField(bytes) { number, _, _ -> counts[number] = (counts[number] ?: 0) + 1 }
        return counts
    }

    /**
     * Bytes of the last occurrence of [fieldNumber], or null when the field is absent. Last wins to
     * match protobuf semantics for repeated fields, so origin detection and a later full decode of
     * the same payload can never disagree about which occurrence is authoritative.
     */
    private fun lastFieldPayload(bytes: ByteArray, fieldNumber: Int): ByteArray? {
        var payload: ByteArray? = null
        forEachTopLevelField(bytes) { number, start, length ->
            if (number == fieldNumber) payload = bytes.copyOfRange(start, start + length)
        }
        return payload
    }

    /**
     * Single allocation-free pass over the top level of a protobuf message. Nested messages are
     * skipped wholesale (not recursed into); [action] receives each field number together with the
     * byte range of its payload.
     */
    private inline fun forEachTopLevelField(
        bytes: ByteArray,
        action: (number: Int, payloadStart: Int, payloadLength: Int) -> Unit,
    ) {
        var pos = 0
        while (pos < bytes.size) {
            val (tag, afterTag) = readVarint(bytes, pos)
            pos = afterTag
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            if (fieldNumber == 0) throw SerializationException("Invalid protobuf field number 0")
            var payloadStart = pos
            pos = when (wireType) {
                0 -> readVarint(bytes, pos).second // varint
                1 -> pos + 8 // 64-bit
                2 -> { // length-delimited
                    val (len, afterLen) = readVarint(bytes, pos)
                    payloadStart = afterLen
                    afterLen + len.toInt()
                }
                5 -> pos + 4 // 32-bit
                else -> throw SerializationException("Unsupported protobuf wire type $wireType")
            }
            if (pos > bytes.size) throw SerializationException("Truncated protobuf message")
            action(fieldNumber, payloadStart, pos - payloadStart)
        }
    }

    /** Reads a base-128 varint. Returns (value, indexAfterVarint). */
    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt()
            result = result or ((b.toLong() and 0x7F) shl shift)
            i++
            if (b and 0x80 == 0) return result to i
            shift += 7
            if (shift >= 64) throw SerializationException("Varint too long")
        }
        throw SerializationException("Truncated varint")
    }
}

package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.lnreader.LNReaderBackup
import eu.kanade.tachiyomi.data.backup.models.TadamiSisterManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.InputStream

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
            scanBytes(bytes).isLegacy
        } catch (_: SerializationException) {
            false
        }
    }

    /** True when the wire format still carries legacy anime/novel payload fields. */
    fun hasLegacyPayloadFields(bytes: ByteArray): Boolean {
        return try {
            scanBytes(bytes).sawLegacyMediaField
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

        val scan = try {
            scanBytes(bytes)
        } catch (_: Exception) {
            return BackupOrigin.TADAMI
        }
        return originFromScan(scan)
    }

    /**
     * Streaming counterpart of [detectOrigin] for a decompressed payload stream.
     *
     * Used to verify what the writer just staged: the payload is proven from the wire without
     * ever materializing it. LNReader containers never reach the writer, so the JSON/ZIP probe
     * of the byte variant is deliberately not repeated here.
     */
    internal fun detectOrigin(input: InputStream): BackupOrigin = originFromScan(scanStream(input))

    /**
     * True only when field 20000 is present *and* decodes into a manifest with our signature and a
     * version this build understands. The presence of the field number alone proves nothing, since
     * any other app is free to use it.
     */
    private fun hasConfirmedSisterManifest(scan: PayloadScan): Boolean {
        val payload = scan.manifestPayload ?: return false
        return try {
            ProtoBuf.decodeFromByteArray(TadamiSisterManifest.serializer(), payload).isValid
        } catch (_: Exception) {
            false
        }
    }

    internal fun originFromScan(scan: PayloadScan): BackupOrigin {
        return when {
            scan.isLegacy -> BackupOrigin.LEGACY_ANIYOMI
            // Our own sister-app export: Mihon shaped, but carrying a manifest we can verify.
            // Checked before the native markers because the export also carries the native anime
            // fields (501-503) for Aniyomi-shaped readers, which would otherwise read as native.
            hasConfirmedSisterManifest(scan) -> BackupOrigin.TADAMI_SISTER
            scan.fields.any { it in NATIVE_MARKER_FIELDS } -> BackupOrigin.TADAMI
            KOMIKKU_FEED_FIELD in scan.fields -> BackupOrigin.KOMIKKU
            TACHIYOMI_SY_SAVED_SEARCH_FIELD in scan.fields -> BackupOrigin.TACHIYOMI_SY
            scan.fields.any { it in MIHON_CONTENT_FIELDS } -> BackupOrigin.MIHON
            else -> BackupOrigin.TADAMI
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
    private const val LEGACY_ANIME_SOURCES_FIELD = 103
    private const val IS_LEGACY_FIELD = 500
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
     * sister export is Mihon shaped: flattened manga and novels both land on field 1 while novels
     * read zero, and anime travels at the native 501/502 numbers for Aniyomi-shaped readers.
     *
     * A repeated message field occurs once per element, so counting top level occurrences is
     * equivalent to the decoded list sizes, at O(1) memory instead of a full object graph.
     */
    fun contentSummary(bytes: ByteArray): BackupContentSummary = summaryFromScan(scanBytes(bytes))

    /** Streaming [contentSummary] over a decompressed payload; see [detectOrigin] for the scope. */
    internal fun contentSummary(input: InputStream): BackupContentSummary =
        summaryFromScan(scanStream(input))

    internal fun summaryFromScan(scan: PayloadScan): BackupContentSummary {
        return BackupContentSummary(
            mangaCount = scan.counts[NATIVE_MANGA_FIELD] ?: 0,
            animeCount = scan.counts[NATIVE_ANIME_FIELD] ?: 0,
            novelCount = scan.counts[NATIVE_NOVEL_FIELD] ?: 0,
            categoriesCount = (scan.counts[NATIVE_CATEGORY_FIELD] ?: 0) +
                (scan.counts[NATIVE_ANIME_CATEGORY_FIELD] ?: 0) +
                (scan.counts[NATIVE_NOVEL_CATEGORY_FIELD] ?: 0),
        )
    }

    /**
     * Everything a single top-level pass over a payload reveals: present field numbers, per field
     * occurrence counts, the legacy markers and the sister manifest bytes (field 20000, last
     * occurrence wins to match protobuf repeated/singular-last semantics).
     */
    internal class PayloadScan {
        val fields = mutableSetOf<Int>()
        val counts = mutableMapOf<Int, Int>()
        var sawLegacyMediaField = false
        var isLegacyVarint: Boolean? = null
        var manifestPayload: ByteArray? = null

        /** Mirrors the old decode-based rule: legacy media fields, or isLegacy with anime sources. */
        val isLegacy: Boolean
            get() = sawLegacyMediaField ||
                ((isLegacyVarint ?: true) && (counts[LEGACY_ANIME_SOURCES_FIELD] ?: 0) > 0)

        fun record(number: Int) {
            fields += number
            counts[number] = (counts[number] ?: 0) + 1
            if (number == LEGACY_ANIME_FIELD || number == LEGACY_NOVEL_FIELD) {
                sawLegacyMediaField = true
            }
        }
    }

    internal fun scanBytes(bytes: ByteArray): PayloadScan {
        val scan = PayloadScan()
        forEachTopLevelField(bytes) { number, start, length ->
            scan.record(number)
            when (number) {
                IS_LEGACY_FIELD -> scan.isLegacyVarint = readVarint(bytes, start).first != 0L
                TadamiSisterManifest.PROTO_FIELD ->
                    scan.manifestPayload = bytes.copyOfRange(start, start + length)
            }
        }
        return scan
    }

    /** Single sequential pass over a decompressed payload stream; nested messages are skipped. */
    internal fun scanStream(input: InputStream): PayloadScan {
        val scan = PayloadScan()
        while (true) {
            val tag = readVarint(input) ?: break
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            if (fieldNumber == 0) throw SerializationException("Invalid protobuf field number 0")
            scan.record(fieldNumber)
            when (wireType) {
                0 -> {
                    val value = readVarint(input)
                        ?: throw SerializationException("Truncated varint")
                    if (fieldNumber == IS_LEGACY_FIELD) scan.isLegacyVarint = value != 0L
                }
                1 -> skipFully(input, 8)
                2 -> {
                    val length = (readVarint(input) ?: throw SerializationException("Truncated varint")).toInt()
                    if (fieldNumber == TadamiSisterManifest.PROTO_FIELD) {
                        scan.manifestPayload = readFully(input, length)
                    } else {
                        skipFully(input, length.toLong())
                    }
                }
                5 -> skipFully(input, 4)
                else -> throw SerializationException("Unsupported protobuf wire type $wireType")
            }
        }
        return scan
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) throw SerializationException("Truncated protobuf message")
            offset += read
        }
        return buffer
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                // skip() may refuse even when bytes remain (some filtered streams): read one byte.
                if (input.read() < 0) throw SerializationException("Truncated protobuf message")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readVarint(input: InputStream): Long? {
        var result = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (shift == 0) return null // clean end of message
                throw SerializationException("Truncated varint")
            }
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift >= 64) throw SerializationException("Varint too long")
        }
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
            i += 1
            if (b and 0x80 == 0) return result to i
            shift += 7
            if (shift >= 64) throw SerializationException("Varint too long")
        }
        throw SerializationException("Truncated varint")
    }
}

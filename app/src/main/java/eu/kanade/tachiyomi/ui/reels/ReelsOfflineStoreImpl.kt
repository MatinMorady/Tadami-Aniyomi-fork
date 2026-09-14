package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class ReelsOfflineStoreImpl(
    private val app: Application = Injekt.get(),
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReelsOfflineStore {

    private val dir: File
        get() = File(app.filesDir, "reels_offline")

    override fun isStored(sourceId: Long, videoId: String): Boolean = fileFor(sourceId, videoId).exists()

    override fun localUrl(sourceId: Long, videoId: String): String? {
        val file = fileFor(sourceId, videoId)
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    override fun storedPairs(): Set<Pair<Long, String>> {
        val files = dir.listFiles() ?: return emptySet()
        return files.asSequence()
            // In-flight downloads are not stored copies.
            .filterNot { it.name.endsWith(".part") }
            .mapNotNull { file -> parseName(file.name) }
            .toSet()
    }

    override suspend fun download(item: ShortVideoItem, sourceId: Long): ReelsOfflineStore.DownloadResult =
        withContext(ioDispatcher) {
            val target = fileFor(sourceId, item.id)
            if (target.exists()) return@withContext ReelsOfflineStore.DownloadResult.SAVED
            dir.mkdirs()
            sweepStaleParts()
            val currentSize = (dir.listFiles() ?: emptyArray()).sumOf { it.length() }
            val request = Request.Builder()
                .url(item.videoUrl)
                .apply { sameOriginReferer(item.webUrl)?.let { header("Referer", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ReelsOfflineStore.DownloadResult.NETWORK
                val expected = response.body.contentLength().coerceAtLeast(0)
                if (expected > 0 && currentSize + expected > ReelsOfflineStore.MAX_OFFLINE_BYTES) {
                    return@withContext ReelsOfflineStore.DownloadResult.QUOTA
                }
                // Unique part name per attempt: a double-tap starts two concurrent downloads and a shared
                // ".part" path would let two writers corrupt each other's file.
                val part = File(dir, "${target.name}.${System.nanoTime()}.part")
                try {
                    response.body.byteStream().use { input ->
                        part.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    // Transport failure or cancellation: never leave an orphan part eating quota.
                    part.delete()
                    throw e
                }
                // Chunked responses report contentLength -1 and skip the pre-check: enforce the
                // quota on the REAL copied size instead (an oversized copy is discarded).
                if (part.length() == 0L) {
                    part.delete()
                    return@withContext ReelsOfflineStore.DownloadResult.NETWORK
                }
                if (currentSize + part.length() > ReelsOfflineStore.MAX_OFFLINE_BYTES) {
                    part.delete()
                    return@withContext ReelsOfflineStore.DownloadResult.QUOTA
                }
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
            }
            if (target.exists()) {
                ReelsOfflineStore.DownloadResult.SAVED
            } else {
                ReelsOfflineStore.DownloadResult.NETWORK
            }
        }

    override suspend fun delete(sourceId: Long, videoId: String): Boolean = withContext(ioDispatcher) {
        val file = fileFor(sourceId, videoId)
        !file.exists() || file.delete()
    }

    override fun usedBytes(): Long = (dir.listFiles() ?: emptyArray()).sumOf { it.length() }

    override suspend fun deleteBySource(sourceId: Long): Int = withContext(ioDispatcher) {
        val prefix = "${sourceId}_"
        (dir.listFiles() ?: emptyArray())
            .filter { it.name.startsWith(prefix) }
            .count { it.delete() }
    }

    override suspend fun clearAll(): Long = withContext(ioDispatcher) {
        val files = dir.listFiles() ?: return@withContext 0L
        files.sumOf { file ->
            val size = file.length()
            if (file.delete()) size else 0L
        }
    }

    private fun fileFor(sourceId: Long, videoId: String): File {
        val ext =
            EXTENSION_BY_NAME[Uri.parse(videoId).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()]
                ?: "mp4"
        return File(dir, "${sourceId}_${android.net.Uri.encode(videoId)}.$ext")
    }

    private fun parseName(name: String): Pair<Long, String>? {
        // "sourceId_encodedVideoId.ext" — the encoded id is reversible, the ext is dropped.
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: return null
        val core = name.substring(0, dot)
        val underscore = core.indexOf('_')
        if (underscore <= 0) return null
        val sourceId = core.substring(0, underscore).toLongOrNull() ?: return null
        val videoId = android.net.Uri.decode(core.substring(underscore + 1)) ?: return null
        return sourceId to videoId
    }

    /** Same-origin Referer, mirroring the player: referer-protected CDNs 403 bare requests. */
    private fun sameOriginReferer(webUrl: String?): String? {
        if (webUrl.isNullOrBlank()) return null
        return runCatching {
            val page = Uri.parse(webUrl)
            val host = page.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
            if (page.scheme != "http" && page.scheme != "https") return@runCatching null
            val port = if (page.port != -1) ":${page.port}" else ""
            "${page.scheme}://$host$port/"
        }.getOrNull()
    }

    /** Orphan .part files (process death mid-download) must not eat quota forever. */
    private fun sweepStaleParts() {
        val cutoff = System.currentTimeMillis() - STALE_PART_MS
        (dir.listFiles() ?: return).forEach { file ->
            if (file.name.endsWith(".part") && file.lastModified() < cutoff) file.delete()
        }
    }

    private companion object {
        const val STALE_PART_MS = 60 * 60 * 1000L

        // Only known progressive containers get their extension baked into the file name;
        // everything else falls back to .mp4 (the player keys off the URI, not the suffix).
        val EXTENSION_BY_NAME = mapOf(
            "mp4" to "mp4",
            "webm" to "webm",
            "m4v" to "m4v",
            "mov" to "mov",
            "mkv" to "mkv",
            "gif" to "gif",
        )
    }
}

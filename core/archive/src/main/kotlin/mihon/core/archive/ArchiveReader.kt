package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.concurrent.Volatile

class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    private val size = pfd.statSize
    private val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)
    private val lock = Any()

    @Volatile
    private var isClosed = false

    // Streams handed out by getInputStream() escape this class and read the mapping on other
    // threads (page holders, SSIV tiles). munmap() while such a stream is alive is a
    // use-after-free that surfaces as a native SIGSEGV, so the unmap is refcounted: close()
    // defers it until the last outstanding stream is closed. All fields below are guarded by [lock].
    private var openStreamCount = 0
    private var munmapPending = false

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = synchronized(lock) {
        check(!isClosed) { "ArchiveReader is closed" }
        val stream = ArchiveInputStream(address, size)
        openStreamCount++
        try {
            stream.use {
                block(generateSequence { stream.getNextEntry() })
            }
        } finally {
            releaseStream()
        }
    }

    fun getInputStream(entryName: String): InputStream? = synchronized(lock) {
        if (isClosed) return null
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    openStreamCount++
                    return object : FilterInputStream(archive) {
                        private var released = false

                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                if (!released) {
                                    released = true
                                    releaseStream()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        null
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            if (openStreamCount > 0) {
                munmapPending = true
                return
            }
        }
        Os.munmap(address, size)
    }

    private fun releaseStream() {
        var shouldMunmap = false
        synchronized(lock) {
            openStreamCount--
            if (openStreamCount == 0 && munmapPending) {
                munmapPending = false
                shouldMunmap = true
            }
        }
        if (shouldMunmap) {
            Os.munmap(address, size)
        }
    }
}

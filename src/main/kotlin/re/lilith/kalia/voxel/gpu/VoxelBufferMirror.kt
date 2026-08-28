package re.lilith.kalia.voxel.gpu

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.voxel.pool.PagedWords
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams one [PagedWords] arena into a storage buffer, uploading only the pages that changed.
 *
 * Uploads are capped per frame so that a world join, which dirties tens of megabytes at once,
 * spreads over several frames instead of stalling the transfer queue. Pages stay marked until they
 * have actually been copied, so nothing is lost when the budget runs out mid-sweep.
 */
class VoxelBufferMirror(private val label: String) : AutoCloseable {
    private var device: RenderDevice? = null
    private var buffer: GpuBuffer? = null
    private var source: PagedWords? = null
    private var uploadedGeneration = -1
    private var staging: ByteBuffer = allocate(INITIAL_STAGING_BYTES)

    /** Bytes copied to the GPU during the most recent [sync]. */
    var lastUploadBytes: Long = 0L
        private set

    val gpuBuffer: GpuBuffer? get() = buffer

    /**
     * Brings the buffer in line with the mirror.
     *
     * @return the storage buffer, or null when it could not be created.
     */
    fun sync(device: RenderDevice, source: PagedWords, budgetBytes: Long): GpuBuffer? {
        lastUploadBytes = 0L

        val existing = buffer
        if (existing == null ||
            this.device !== device ||
            this.source !== source ||
            uploadedGeneration != source.generation
        ) {
            existing?.close()
            this.device = device
            this.source = source
            source.markAllDirty()
            buffer = device.createBuffer(
                BufferDescription(
                    label = label,
                    sizeBytes = source.capacity.toLong() * Int.SIZE_BYTES,
                    usage = BufferUsage.STORAGE,
                ),
            )
            uploadedGeneration = source.generation
        }

        val target = buffer ?: return null
        if (!source.hasDirty()) {
            return target
        }

        val words = source.words
        var remaining = budgetBytes
        source.forEachDirtyRange { firstWord, wordCount ->
            if (remaining > 0L) {
                var cursor = firstWord
                var left = minOf(wordCount, source.capacity - firstWord)
                while (left > 0 && remaining > 0L) {
                    val chunk = minOf(left, (remaining / Int.SIZE_BYTES).toInt(), STAGING_WORDS)
                    if (chunk <= 0) {
                        break
                    }
                    upload(target, words, cursor, chunk)
                    source.clearDirtyRange(cursor, chunk)
                    remaining -= chunk.toLong() * Int.SIZE_BYTES
                    lastUploadBytes += chunk.toLong() * Int.SIZE_BYTES
                    cursor += chunk
                    left -= chunk
                }
            }
        }
        return target
    }

    private fun upload(target: GpuBuffer, words: IntArray, firstWord: Int, wordCount: Int) {
        val bytes = wordCount * Int.SIZE_BYTES
        if (staging.capacity() < bytes) {
            staging = allocate(bytes)
        }
        staging.clear()
        staging.limit(bytes)
        staging.asIntBuffer().put(words, firstWord, wordCount)
        target.write(staging, firstWord.toLong() * Int.SIZE_BYTES)
    }

    override fun close() {
        buffer?.close()
        buffer = null
        device = null
        source = null
        uploadedGeneration = -1
    }

    private companion object {
        /** 1 MiB of scratch, which is also the largest single copy handed to the staging pool. */
        const val STAGING_WORDS = 256 * 1024
        const val INITIAL_STAGING_BYTES = 64 * 1024

        fun allocate(bytes: Int): ByteBuffer =
            ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    }
}

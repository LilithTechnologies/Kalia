package re.lilith.kalia.buffer

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DirectBufferPool {
    fun acquire(byteCount: Int): ByteBuffer {
        val capacity = capacityFor(byteCount)
        val sizeClass = sizeClassOf(capacity)
        if (sizeClass >= 0) {
            classes[sizeClass].removeLastOrNull()?.let { pooled ->
                pooledBytes -= pooled.capacity()
                pooled.clear()
                return pooled
            }
        }
        return MemoryUtil.memAlloc(capacity).order(ByteOrder.nativeOrder())
    }

    fun release(buffer: ByteBuffer) {
        val sizeClass = sizeClassOf(buffer.capacity())
        if (sizeClass >= 0 &&
            classes[sizeClass].size < MAX_BUFFERS_PER_CLASS &&
            pooledBytes + buffer.capacity() <= MAX_POOLED_BYTES
        ) {
            classes[sizeClass].addLast(buffer)
            pooledBytes += buffer.capacity()
            return
        }
        MemoryUtil.memFree(buffer)
    }

    fun drain() {
        for (deque in classes) {
            while (true) {
                MemoryUtil.memFree(deque.removeLastOrNull() ?: break)
            }
        }
        pooledBytes = 0L
    }

    private fun capacityFor(byteCount: Int): Int {
        val requested = byteCount.coerceAtLeast(1)
        var capacity = Integer.highestOneBit(requested)
        if (capacity < requested) {
            capacity = capacity shl 1
        }
        return capacity.coerceAtLeast(1 shl MIN_SHIFT)
    }

    private fun sizeClassOf(capacity: Int): Int {
        if (capacity != Integer.highestOneBit(capacity)) {
            return -1
        }
        val shift = Integer.numberOfTrailingZeros(capacity)
        if (shift !in MIN_SHIFT..MAX_SHIFT) {
            return -1
        }
        return shift - MIN_SHIFT
    }

    private const val MIN_SHIFT = 12
    private const val MAX_SHIFT = 22
    private const val MAX_BUFFERS_PER_CLASS = 128
    private const val MAX_POOLED_BYTES = 32L shl 20

    private val classes = Array(MAX_SHIFT - MIN_SHIFT + 1) { ArrayDeque<ByteBuffer>() }
    private var pooledBytes = 0L
}

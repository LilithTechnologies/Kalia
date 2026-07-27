package re.lilith.kalia.draw

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

object TextMeshCache {
    const val PAGE_DEFAULT: Int = -1
    const val PAGE_DECORATION: Int = -2

    private const val MAX_ENTRIES = 2048

    class Segment(
        @JvmField val page: Int,
        @JvmField val vertexData: ByteBuffer,
        @JvmField val vertexCount: Int,
    )

    class CachedText(
        @JvmField val segments: Array<Segment>,
        @JvmField val advance: Float,
    ) {
        fun free() {
            for (segment in segments) {
                releaseSegmentBuffer(segment.vertexData)
            }
        }
    }
    private const val MIN_POOL_SHIFT = 6 // 64 B
    private const val MAX_POOL_SHIFT = 16 // 64 KiB
    private const val MAX_BUFFERS_PER_CLASS = 64
    private const val MAX_POOLED_BYTES = 8L shl 20 // 8 MiB

    private val pooledBuffers = Array(MAX_POOL_SHIFT - MIN_POOL_SHIFT + 1) { ArrayDeque<ByteBuffer>() }
    private var pooledBytes = 0L

    private fun poolIndexForCapacity(capacity: Int): Int {
        if (capacity != Integer.highestOneBit(capacity)) return -1 // not power-of-two! unpooled
        val shift = Integer.numberOfTrailingZeros(capacity)
        if (shift !in MIN_POOL_SHIFT..MAX_POOL_SHIFT) return -1
        return shift - MIN_POOL_SHIFT
    }

    @JvmStatic
    fun allocSegmentBuffer(length: Int): ByteBuffer {
        var capacity = Integer.highestOneBit(length.coerceAtLeast(1))
        if (capacity < length) capacity = capacity shl 1
        capacity = capacity.coerceAtLeast(1 shl MIN_POOL_SHIFT)

        val index = poolIndexForCapacity(capacity)
        if (index >= 0) {
            pooledBuffers[index].removeLastOrNull()?.let { pooled ->
                pooledBytes -= pooled.capacity()
                pooled.clear()
                return pooled
            }
        }
        return MemoryUtil.memAlloc(capacity)
    }

    @JvmStatic
    fun releaseSegmentBuffer(buffer: ByteBuffer) {
        val index = poolIndexForCapacity(buffer.capacity())
        if (index >= 0 &&
            pooledBuffers[index].size < MAX_BUFFERS_PER_CLASS &&
            pooledBytes + buffer.capacity() <= MAX_POOLED_BYTES
        ) {
            pooledBuffers[index].addLast(buffer)
            pooledBytes += buffer.capacity()
        } else {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun drainPool() {
        for (deque in pooledBuffers) {
            while (true) {
                MemoryUtil.memFree(deque.removeLastOrNull() ?: break)
            }
        }
        pooledBytes = 0L
    }

    class Key(
        @JvmField var text: String,
        @JvmField var shadow: Boolean,
        @JvmField var color: Int,
        @JvmField var unicode: Boolean,
        @JvmField var styleBits: Int,
    ) {
        fun set(text: String, shadow: Boolean, color: Int, unicode: Boolean, styleBits: Int): Key {
            this.text = text
            this.shadow = shadow
            this.color = color
            this.unicode = unicode
            this.styleBits = styleBits
            return this
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Key) return false
            return color == other.color &&
                styleBits == other.styleBits &&
                shadow == other.shadow &&
                unicode == other.unicode &&
                text == other.text
        }

        override fun hashCode(): Int {
            var h = text.hashCode()
            h = h * 31 + color
            h = h * 31 + styleBits
            h = h * 31 + (if (shadow) 1 else 0)
            h = h * 31 + (if (unicode) 2 else 0)
            return h
        }
    }

    private val lookupKey = Key("", false, 0, false, 0)

    private val cache = object : LinkedHashMap<Key, CachedText>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CachedText>): Boolean {
            if (size > MAX_ENTRIES) {
                eldest.value.free()
                return true
            }
            return false
        }
    }

    @JvmStatic
    fun find(text: String, shadow: Boolean, color: Int, unicode: Boolean, styleBits: Int): CachedText? =
        cache[lookupKey.set(text, shadow, color, unicode, styleBits)]

    @JvmStatic
    fun put(text: String, shadow: Boolean, color: Int, unicode: Boolean, styleBits: Int, value: CachedText) {
        cache.put(Key(text, shadow, color, unicode, styleBits), value)?.free()
    }

    @JvmStatic
    fun clear() {
        cache.values.forEach(CachedText::free)
        cache.clear()
        drainPool()
    }
}
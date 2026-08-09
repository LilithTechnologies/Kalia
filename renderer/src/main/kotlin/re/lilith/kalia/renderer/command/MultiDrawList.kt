package re.lilith.kalia.renderer.command

import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.utility.MemoryAccess

/**
 * A fixed-capacity list of indexed draws submitted together through [PassEncoder.multiDrawIndexed]
 *
 * @author Lunasa
 * @since 1.0.0
 */
class MultiDrawList(val capacity: Int, val layout: MultiDrawLayout = MultiDrawLayout.SEQUENTIAL) {
    val stride: Int get() = layout.stride

    val buffer = MemoryUtil.nmemAlloc(capacity.toLong() * layout.stride)

    var size: Int = 0
        private set

    var maxIndexCount: Int = 0
        private set

    val isEmpty: Boolean get() = size <= 0

    val sizeBytes: Long get() = size.toLong() * layout.stride

    init {
        if (layout == MultiDrawLayout.INDIRECT) {
            for (draw in 0 until capacity) {
                val base = buffer + (draw.toLong() * layout.stride)
                MemoryAccess.putInt(base + INSTANCE_COUNT_OFFSET, 1)
                MemoryAccess.putInt(base + FIRST_INSTANCE_OFFSET, 0)
            }
        }
    }

    fun clear() {
        size = 0
        maxIndexCount = 0
    }

    fun addDraw(indexCount: Int, firstIndex: Int, vertexOffset: Int) {
        if (indexCount <= 0) {
            return
        }
        check(size < capacity) { "MultiDrawList capacity ($capacity) exceeded." }
        val base = buffer + (size.toLong() * layout.stride)
        MemoryAccess.putInt(base + layout.firstIndexOffset, firstIndex)
        MemoryAccess.putInt(base + layout.indexCountOffset, indexCount)
        MemoryAccess.putInt(base + layout.vertexOffsetOffset, vertexOffset)
        size++
        if (indexCount > maxIndexCount) {
            maxIndexCount = indexCount
        }
    }

    fun firstIndex(draw: Int): Int =
        MemoryAccess.getInt(buffer + (draw.toLong() * layout.stride) + layout.firstIndexOffset)

    fun indexCount(draw: Int): Int =
        MemoryAccess.getInt(buffer + (draw.toLong() * layout.stride) + layout.indexCountOffset)

    fun vertexOffset(draw: Int): Int =
        MemoryAccess.getInt(buffer + (draw.toLong() * layout.stride) + layout.vertexOffsetOffset)

    private companion object {
        const val INSTANCE_COUNT_OFFSET = 4
        const val FIRST_INSTANCE_OFFSET = 16
    }
}

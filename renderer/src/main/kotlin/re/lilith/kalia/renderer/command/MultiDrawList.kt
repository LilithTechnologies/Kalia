package re.lilith.kalia.renderer.command

import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A fixed-capacity list of indexed draws submitted together through [PassEncoder.multiDrawIndexed]
 *
 * @author Lunasa
 * @since 1.0.0
 */
class MultiDrawList(val capacity: Int) {
    val buffer = MemoryUtil.nmemAlloc((capacity * STRIDE).toLong())

    var size: Int = 0
        private set

    val isEmpty: Boolean get() = size <= 0

    fun clear() {
        size = 0
    }

    fun addDraw(indexCount: Int, firstIndex: Int, vertexOffset: Int) {
        check(size < capacity) { "MultiDrawList capacity ($capacity) exceeded." }
        val base = buffer + (size * STRIDE)
        MemoryAccess.putInt(base, firstIndex)
        MemoryAccess.putInt(base + 4, indexCount)
        MemoryAccess.putInt(base + 8, vertexOffset)
        size++
    }

    fun firstIndex(draw: Int): Int = MemoryAccess.getInt(buffer + (draw * STRIDE))

    fun indexCount(draw: Int): Int = MemoryAccess.getInt(buffer + (draw * STRIDE + 4))

    fun vertexOffset(draw: Int): Int = MemoryAccess.getInt(buffer + (draw * STRIDE + 8))

    fun maxIndexCount(): Int {
        var largest = 0
        for (draw in 0 until size) {
            val count = indexCount(draw)
            if (count > largest) {
                largest = count
            }
        }
        return largest
    }

    companion object {
        const val STRIDE = 12
    }
}

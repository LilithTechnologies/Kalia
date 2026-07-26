package re.lilith.kalia.renderer.command

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A fixed-capacity list of indexed draws submitted together through [PassEncoder.multiDrawIndexed]
 */
class MultiDrawList(val capacity: Int) {
    val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(capacity * STRIDE)
        .order(ByteOrder.nativeOrder())

    var size: Int = 0
        private set

    val isEmpty: Boolean get() = size <= 0

    fun clear() {
        size = 0
    }

    fun addDraw(indexCount: Int, firstIndex: Int, vertexOffset: Int) {
        check(size < capacity) { "MultiDrawList capacity ($capacity) exceeded." }
        val base = size * STRIDE
        buffer.putInt(base, firstIndex)
        buffer.putInt(base + 4, indexCount)
        buffer.putInt(base + 8, vertexOffset)
        size++
    }

    fun firstIndex(draw: Int): Int = buffer.getInt(draw * STRIDE)

    fun indexCount(draw: Int): Int = buffer.getInt(draw * STRIDE + 4)

    fun vertexOffset(draw: Int): Int = buffer.getInt(draw * STRIDE + 8)

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

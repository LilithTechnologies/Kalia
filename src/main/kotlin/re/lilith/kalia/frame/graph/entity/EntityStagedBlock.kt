package re.lilith.kalia.frame.graph.entity

import re.lilith.kalia.buffer.DirectBufferPool
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer

internal class EntityStagedBlock(private val bytesPerInstance: Int) {
    var signature: Long = Long.MIN_VALUE
    var count: Int = 0
        private set

    private var instances: ByteBuffer = DirectBufferPool.acquire(bytesPerInstance * INITIAL_PARTS)
    private var locals = FloatArray(MATRIX_FLOATS * INITIAL_PARTS)

    val instanceData: ByteBuffer get() = instances

    fun reset(signature: Long) {
        this.signature = signature
        count = 0
    }

    fun add(source: Long, local: FloatArray) {
        ensure(count + 1)
        val offset = count * bytesPerInstance
        MemoryAccess.copyMemory(
            source,
            MemoryAccess.addressOf(instances) + offset,
            bytesPerInstance.toLong(),
        )
        System.arraycopy(local, 0, locals, count * MATRIX_FLOATS, MATRIX_FLOATS)
        count++
    }

    fun localInto(index: Int, target: FloatArray) {
        System.arraycopy(locals, index * MATRIX_FLOATS, target, 0, MATRIX_FLOATS)
    }

    fun addressOf(index: Int): Long =
        MemoryAccess.addressOf(instances) + index * bytesPerInstance

    fun release() {
        DirectBufferPool.release(instances)
        count = 0
        signature = Long.MIN_VALUE
    }

    private fun ensure(parts: Int) {
        if (parts * bytesPerInstance <= instances.capacity()) {
            return
        }
        val grown = DirectBufferPool.acquire(parts * bytesPerInstance * 2)
        MemoryAccess.copyMemory(
            MemoryAccess.addressOf(instances),
            MemoryAccess.addressOf(grown),
            (count * bytesPerInstance).toLong(),
        )
        DirectBufferPool.release(instances)
        instances = grown
        if (parts * MATRIX_FLOATS > locals.size) {
            locals = locals.copyOf(parts * MATRIX_FLOATS * 2)
        }
    }

    private companion object {
        const val INITIAL_PARTS = 24
        const val MATRIX_FLOATS = 16
    }
}

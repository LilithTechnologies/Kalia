package re.lilith.kalia.frame.graph.entity.nametag

import re.lilith.kalia.buffer.DirectBufferPool
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer

internal class NametagStagedBlock(private val bytesPerInstance: Int) {
    private var data: ByteBuffer = DirectBufferPool.acquire(bytesPerInstance * INITIAL_GLYPHS)

    var count: Int = 0
        private set

    val address: Long get() = MemoryAccess.addressOf(data)

    fun reset() {
        count = 0
    }

    fun append(source: Long, instances: Int) {
        ensure(count + instances)
        MemoryAccess.copyMemory(
            source,
            address + count.toLong() * bytesPerInstance,
            instances.toLong() * bytesPerInstance,
        )
        count += instances
    }

    fun release() {
        DirectBufferPool.release(data)
        count = 0
    }

    private fun ensure(instances: Int) {
        val required = instances * bytesPerInstance
        if (required <= data.capacity()) {
            return
        }
        val grown = DirectBufferPool.acquire(required * 2)
        MemoryAccess.copyMemory(address, MemoryAccess.addressOf(grown), (count * bytesPerInstance).toLong())
        DirectBufferPool.release(data)
        data = grown
    }

    private companion object {
        const val INITIAL_GLYPHS = 64
    }
}

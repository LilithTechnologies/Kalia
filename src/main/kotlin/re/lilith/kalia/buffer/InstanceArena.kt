package re.lilith.kalia.buffer

import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer

class InstanceArena(private val bytesPerInstance: Int, initialInstances: Int) {
    private var data = DirectBufferPool.acquire(bytesPerInstance * initialInstances)
    private var baseAddress = MemoryAccess.addressOf(data)

    var count: Int = 0
        private set

    fun reserve(instances: Int = 1): Long {
        val needed = instances * bytesPerInstance
        if (data.remaining() < needed) {
            grow(needed)
        }
        val address = baseAddress + data.position()
        data.position(data.position() + needed)
        count += instances
        return address
    }

    fun finish(): ByteBuffer {
        data.flip()
        return data
    }

    fun reset() {
        data.clear()
        count = 0
    }

    fun release() {
        DirectBufferPool.release(data)
        count = 0
    }

    private fun grow(needed: Int) {
        val used = data.position()
        var capacity = data.capacity()
        do {
            capacity = capacity shl 1
        } while (capacity - used < needed)

        val grown = DirectBufferPool.acquire(capacity)
        val grownAddress = MemoryAccess.addressOf(grown)
        MemoryAccess.copyMemory(baseAddress, grownAddress, used.toLong())
        grown.position(used)

        DirectBufferPool.release(data)
        data = grown
        baseAddress = grownAddress
    }
}

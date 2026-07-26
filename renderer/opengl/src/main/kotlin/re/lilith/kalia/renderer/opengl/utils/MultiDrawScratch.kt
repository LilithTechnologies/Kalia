package re.lilith.kalia.renderer.opengl.utils

import org.lwjgl.system.MemoryUtil

internal class MultiDrawScratch : AutoCloseable {
    var counts = MemoryUtil.memAllocInt(INITIAL_CAPACITY)
        private set
    var offsets = MemoryUtil.memAllocPointer(INITIAL_CAPACITY)
        private set
    var baseVertices = MemoryUtil.memAllocInt(INITIAL_CAPACITY)
        private set

    fun ensureCapacity(draws: Int) {
        if (counts.capacity() >= draws) {
            counts.clear()
            offsets.clear()
            baseVertices.clear()
            return
        }
        var capacity = counts.capacity()
        while (capacity < draws) {
            capacity *= 2
        }
        MemoryUtil.memFree(counts)
        MemoryUtil.memFree(offsets)
        MemoryUtil.memFree(baseVertices)
        counts = MemoryUtil.memAllocInt(capacity)
        offsets = MemoryUtil.memAllocPointer(capacity)
        baseVertices = MemoryUtil.memAllocInt(capacity)
    }

    override fun close() {
        MemoryUtil.memFree(counts)
        MemoryUtil.memFree(offsets)
        MemoryUtil.memFree(baseVertices)
    }

    private companion object {
        const val INITIAL_CAPACITY = 1024
    }
}

package re.lilith.kalia.renderer.opengl.utils

import org.lwjgl.opengl.ARBBufferStorage.glBufferStorage
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL30C.glMapBufferRange
import org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT
import org.lwjgl.opengl.GL44C.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class PushConstantRing(private val persistent: Boolean) : AutoCloseable {
    var id: Int = 0
        private set

    private var capacity = 0L
    private var offset = 0L
    private var mapped: ByteBuffer? = null

    init {
        allocate(INITIAL_CAPACITY)
    }

    fun write(data: ByteBuffer, alignment: Int): Long {
        val size = data.remaining().toLong()
        val aligned = (offset + alignment - 1) / alignment * alignment
        if (aligned + maxOf(size, MIN_RANGE) > capacity) {
            grow(maxOf(capacity * 2, aligned + size + MIN_RANGE))
            return write(data, alignment)
        }

        val view = mapped
        if (view != null) {
            val target = view.duplicate()
            target.position(aligned.toInt())
            target.put(data.duplicate())
        } else {
            glBindBuffer(GL_UNIFORM_BUFFER, id)
            nglBufferSubData(GL_UNIFORM_BUFFER, aligned, size, MemoryUtil.memAddress(data))
        }

        offset = aligned + size
        return aligned
    }

    fun reset() {
        offset = 0L
    }

    private fun grow(newCapacity: Long) {
        glDeleteBuffers(id)
        allocate(newCapacity)
    }

    private fun allocate(newCapacity: Long) {
        id = glGenBuffers()
        capacity = newCapacity
        offset = 0L
        glBindBuffer(GL_UNIFORM_BUFFER, id)
        if (persistent) {
            val flags = GL_DYNAMIC_STORAGE_BIT or GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT
            glBufferStorage(GL_UNIFORM_BUFFER, newCapacity, flags)
            mapped = glMapBufferRange(GL_UNIFORM_BUFFER, 0L, newCapacity, flags)?.order(ByteOrder.nativeOrder())
        } else {
            glBufferData(GL_UNIFORM_BUFFER, newCapacity, GL_STREAM_DRAW)
            mapped = null
        }
    }

    override fun close() {
        glDeleteBuffers(id)
        id = 0
        mapped = null
    }

    private companion object {
        const val INITIAL_CAPACITY = 4L * 1024L * 1024L
        const val MIN_RANGE = 256L
    }
}

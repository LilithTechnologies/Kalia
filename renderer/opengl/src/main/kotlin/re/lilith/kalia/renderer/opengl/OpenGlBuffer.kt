package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.ARBBufferStorage.glBufferStorage
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL30C.glMapBufferRange
import org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER
import org.lwjgl.opengl.GL44C.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT
import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class OpenGlBuffer(
    private val owner: OpenGlRenderDevice,
    override val label: String,
    override val sizeBytes: Long,
    override val usage: BufferUsage,
    val id: Int,
    private val mappedView: ByteBuffer?,
    private val shadow: ByteBuffer?,
) : GpuBuffer {
    private var closed = false

    override val isClosed: Boolean get() = closed

    override fun mapped(): ByteBuffer? {
        check(!closed) { "Buffer '$label' is closed." }
        return mappedView ?: shadow
    }

    override fun write(source: ByteBuffer, offsetBytes: Long) {
        check(!closed) { "Buffer '$label' is closed." }
        val length = source.remaining().toLong()
        require(offsetBytes >= 0 && offsetBytes + length <= sizeBytes) {
            "Write of $length bytes at $offsetBytes overflows buffer '$label' ($sizeBytes bytes)."
        }
        if (length == 0L) {
            return
        }

        when {
            mappedView != null -> {
                val target = mappedView.duplicate()
                target.position(offsetBytes.toInt())
                target.put(source.duplicate())
            }

            shadow != null -> {
                val target = shadow.duplicate()
                target.position(offsetBytes.toInt())
                target.put(source.duplicate())
                upload(source, offsetBytes)
            }

            else -> upload(source, offsetBytes)
        }
    }

    private fun upload(source: ByteBuffer, offsetBytes: Long) {
        glBindBuffer(GL_COPY_WRITE_BUFFER, id)
        nglBufferSubData(
            GL_COPY_WRITE_BUFFER,
            offsetBytes,
            source.remaining().toLong(),
            MemoryUtil.memAddress(source),
        )
    }

    fun syncShadowRange(offsetBytes: Long, sizeBytes: Long) {
        val source = shadow ?: return
        if (sizeBytes <= 0L) {
            return
        }
        val slice = source.duplicate()
        slice.position(offsetBytes.toInt())
        slice.limit((offsetBytes + sizeBytes).toInt())
        upload(slice, offsetBytes)
    }

    override fun close() {
        if (closed) return
        closed = true
        val buffer = id
        owner.scheduleRelease { glDeleteBuffers(buffer) }
    }

    companion object {
        fun create(owner: OpenGlRenderDevice, label: String, sizeBytes: Long, usage: BufferUsage): OpenGlBuffer {
            require(usage != BufferUsage.STORAGE) {
                "Buffer '$label': the OpenGL backend targets 4.1 core, which has no shader storage buffers."
            }

            val id = glGenBuffers()
            glBindBuffer(GL_COPY_WRITE_BUFFER, id)

            var mapped: ByteBuffer? = null
            var shadow: ByteBuffer? = null
            when (usage) {
                BufferUsage.STREAM if owner.context.supportsBufferStorage -> {
                    val flags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT
                    glBufferStorage(GL_COPY_WRITE_BUFFER, sizeBytes, flags)
                    mapped = checkNotNull(glMapBufferRange(GL_COPY_WRITE_BUFFER, 0L, sizeBytes, flags)) {
                        "Persistent mapping of buffer '$label' failed."
                    }.order(ByteOrder.nativeOrder())
                }

                BufferUsage.STREAM -> {
                    glBufferData(GL_COPY_WRITE_BUFFER, sizeBytes, GL_STREAM_DRAW)
                    shadow = ByteBuffer.allocateDirect(sizeBytes.toInt()).order(ByteOrder.nativeOrder())
                }

                else -> glBufferData(GL_COPY_WRITE_BUFFER, sizeBytes, GL_STATIC_DRAW)
            }

            return OpenGlBuffer(owner, label, sizeBytes, usage, id, mapped, shadow)
        }
    }
}

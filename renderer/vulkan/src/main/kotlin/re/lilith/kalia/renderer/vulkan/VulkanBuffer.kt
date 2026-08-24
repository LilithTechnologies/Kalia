package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import re.lilith.vulkan.api.memory.Buffer as VkBuffer

internal class VulkanBuffer(
    private val owner: VulkanRenderDevice,
    override val label: String,
    override val sizeBytes: Long,
    override val usage: BufferUsage,
    val buffer: VkBuffer,
) : GpuBuffer {
    private var closed = false

    private val mappedView: ByteBuffer? by lazy {
        if (buffer.isMapped) buffer.mappedByteBuffer(0L, sizeBytes) else null
    }

    override val isClosed: Boolean get() = closed

    override fun mapped(): ByteBuffer? {
        check(!closed) { "Buffer '$label' is closed." }
        return mappedView
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

        if (!buffer.isMapped) {
            owner.uploads.stageBufferWrite(buffer, offsetBytes, source)
            return
        }

        if (source.isDirect) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), buffer.mappedAddress + offsetBytes, length)
        } else {
            buffer.mappedByteBuffer(offsetBytes, length).put(source.duplicate())
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        owner.scheduleRelease(buffer)
    }
}

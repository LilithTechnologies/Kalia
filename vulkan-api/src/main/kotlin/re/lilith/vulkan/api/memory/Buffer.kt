package re.lilith.vulkan.api.memory

import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource
import java.nio.ByteBuffer

class Buffer internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: BufferConfig,
    private val allocator: Long = 0L,
    private val allocation: Long = 0L,
    /** Address of the persistent host mapping, or 0 if the buffer is not mapped. */
    val mappedAddress: Long = 0L,
) : VulkanResource() {
    val size: Long
        get() = config.size

    /** Whether this buffer was created with a persistent host mapping. */
    val isMapped: Boolean
        get() = mappedAddress != 0L

    /**
     * Returns a [ByteBuffer] view over the persistent host mapping for `[offset, offset + size)`.
     * Only valid for buffers created through [MemoryAllocator] with a host-visible [MemoryUsage].
     */
    fun mappedByteBuffer(offset: Long = 0L, size: Long = this.size - offset): ByteBuffer {
        require(isMapped) { "Buffer is not persistently mapped." }
        require(offset >= 0L) { "offset must be >= 0." }
        require(size >= 0L) { "size must be >= 0." }
        require(offset + size <= this.size) { "Mapped range must fit within the buffer." }
        require(size <= Int.MAX_VALUE.toLong()) { "Mapped range must fit within a JVM ByteBuffer." }
        return MemoryUtil.memByteBuffer(mappedAddress + offset, size.toInt())
    }

    override fun closeResource() {
        if (allocation != 0L) {
            Vma.vmaDestroyBuffer(allocator, handle, allocation)
        } else {
            VK10.vkDestroyBuffer(device.handle, handle, null)
        }
    }
}

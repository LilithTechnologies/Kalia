package re.lilith.vulkan.api.memory

import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.command.BarrierImage
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class Image internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: ImageConfig,
    private val allocator: Long = 0L,
    private val allocation: Long = 0L,
) : VulkanResource(), BarrierImage {
    override fun closeResource() {
        if (allocation != 0L) {
            Vma.vmaDestroyImage(allocator, handle, allocation)
        } else {
            VK10.vkDestroyImage(device.handle, handle, null)
        }
    }
}

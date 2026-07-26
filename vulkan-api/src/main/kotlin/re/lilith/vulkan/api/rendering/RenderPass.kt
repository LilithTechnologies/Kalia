package re.lilith.vulkan.api.rendering

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class RenderPass internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val layout: RenderPassLayout,
) : VulkanResource() {
    override fun closeResource() {
        VK10.vkDestroyRenderPass(device.handle, handle, null)
    }
}
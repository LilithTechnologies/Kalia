package re.lilith.vulkan.api.rendering

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class Framebuffer internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val renderPass: RenderPass,
    val config: FramebufferConfig,
) : VulkanResource() {
    override fun closeResource() {
        VK10.vkDestroyFramebuffer(device.handle, handle, null)
    }
}
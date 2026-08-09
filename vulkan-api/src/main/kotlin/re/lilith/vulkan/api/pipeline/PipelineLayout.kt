package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class PipelineLayout internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: PipelineLayoutConfig,
) : VulkanResource() {
    override fun closeResource() {
        VK10.vkDestroyPipelineLayout(device.handle, handle, null)
    }
}
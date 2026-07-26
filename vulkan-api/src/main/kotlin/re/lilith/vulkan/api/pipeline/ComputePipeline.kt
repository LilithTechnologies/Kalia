package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class ComputePipeline internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: ComputePipelineConfig,
) : VulkanResource() {
    override fun closeResource() {
        VK10.vkDestroyPipeline(device.handle, handle, null)
    }
}


package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class Sampler internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: SamplerConfig,
) : VulkanResource() {
    override fun closeResource() {
        VK10.vkDestroySampler(device.handle, handle, null)
    }
}


package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

class DescriptorSetLayout internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: DescriptorSetLayoutConfig,
) : VulkanResource() {
    val bindings: List<DescriptorSetLayoutBinding>
        get() = config.bindings

    val isPushDescriptor: Boolean
        get() = config.isPushDescriptor

    override fun closeResource() {
        VK10.vkDestroyDescriptorSetLayout(device.handle, handle, null)
    }
}


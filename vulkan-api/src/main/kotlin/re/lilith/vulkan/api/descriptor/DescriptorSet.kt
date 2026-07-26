package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.device.LogicalDevice

class DescriptorSet internal constructor(
    val pool: DescriptorPool,
    val layout: DescriptorSetLayout,
    internal val handle: Long,
) {
    val device: LogicalDevice
        get() = pool.device
}


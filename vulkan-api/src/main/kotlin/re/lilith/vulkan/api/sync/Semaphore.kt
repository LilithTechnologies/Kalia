package re.lilith.vulkan.api.sync

import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource

sealed class Semaphore protected constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
) : VulkanResource()
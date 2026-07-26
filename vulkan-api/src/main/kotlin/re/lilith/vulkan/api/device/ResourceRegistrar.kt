package re.lilith.vulkan.api.device

import re.lilith.vulkan.api.resource.VulkanResource

internal interface ResourceRegistrar {
    fun <T : VulkanResource> register(resource: T): T
}


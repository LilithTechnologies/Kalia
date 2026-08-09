package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.internal.vk.VulkanConstants

internal val SubpassReference.vkIndex: Int
    get() = when (this) {
        SubpassReference.External -> VulkanConstants.Subpasses.external
        is SubpassReference.Index -> value
    }

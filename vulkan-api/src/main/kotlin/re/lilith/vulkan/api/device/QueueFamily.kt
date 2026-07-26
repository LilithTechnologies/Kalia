package re.lilith.vulkan.api.device

import re.lilith.vulkan.api.types.flags.QueueCapability
import re.lilith.vulkan.api.types.geometry.Extent3D

/**
 * Static description of a queue family exposed by a physical device.
 */
data class QueueFamily(
    val index: Int,
    val capabilities: QueueCapability,
    val queueCount: Int,
    val timestampValidBits: Int,
    val minImageTransferGranularity: Extent3D,
)
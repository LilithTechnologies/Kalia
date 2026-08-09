package re.lilith.vulkan.api.device

import re.lilith.vulkan.api.core.Version
import re.lilith.vulkan.api.types.enum.PhysicalDeviceType

data class PhysicalDevicePropertiesView(
    val name: String,
    val type: PhysicalDeviceType,
    val apiVersion: Version,
    val driverVersion: Version,
    val vendorId: Int,
    val deviceId: Int,
    val maxBoundDescriptorSets: Int,
    val maxColorAttachments: Int,
    val maxImageDimension2D: Int,
    val maxPushConstantsSize: Int,
    val maxPushDescriptors: Int? = null,
    val maxMultiDrawCount: Int? = null,
    val subTexelPrecisionBits: Int,
    val timestampPeriod: Float,
    val maxSamplerAnisotropy: Float,
)

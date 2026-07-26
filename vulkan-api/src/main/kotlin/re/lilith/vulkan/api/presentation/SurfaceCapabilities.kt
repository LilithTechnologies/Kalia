package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.types.geometry.Extent2D

data class SurfaceCapabilities(
    val minimumImageCount: Int,
    val maximumImageCount: Int,
    val currentExtent: Extent2D?,
    val minimumImageExtent: Extent2D,
    val maximumImageExtent: Extent2D,
    val currentTransformBits: Int,
    val supportedUsageFlags: Int,
)
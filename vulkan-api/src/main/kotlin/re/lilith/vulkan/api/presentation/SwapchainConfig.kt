package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.geometry.Extent2D

data class SwapchainConfig(
    val extent: Extent2D,
    val imageCount: Int = 2,
    val preferredFormat: Format = Format.B8G8R8A8_UNorm,
    val preferredColorSpace: ColorSpace = ColorSpace.SrgbNonLinear,
    /** Ordered by preference; the first mode the surface actually supports wins, else Fifo. */
    val preferredPresentModes: List<PresentMode> = listOf(PresentMode.Fifo),
    val queueFamilyIndices: List<Int> = emptyList(),
)

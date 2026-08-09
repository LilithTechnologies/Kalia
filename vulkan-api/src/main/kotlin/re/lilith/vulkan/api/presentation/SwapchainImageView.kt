package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.rendering.RenderingImageView

class SwapchainImageView internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val index: Int,
) : RenderingImageView

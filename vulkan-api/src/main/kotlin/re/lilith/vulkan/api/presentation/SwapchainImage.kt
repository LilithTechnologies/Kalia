package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.command.BarrierImage
import re.lilith.vulkan.api.device.LogicalDevice

class SwapchainImage internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val index: Int,
    val view: SwapchainImageView,
) : BarrierImage

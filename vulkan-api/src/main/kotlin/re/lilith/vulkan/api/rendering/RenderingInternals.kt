package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.presentation.SwapchainImageView

internal val RenderingImageView.nativeHandle: Long
    get() = when (this) {
        is ImageView -> handle
        is SwapchainImageView -> handle
        else -> error("Unsupported rendering image view type: ${this::class.qualifiedName}")
    }

internal val RenderingImageView.ownerDevice: LogicalDevice
    get() = when (this) {
        is ImageView -> device
        is SwapchainImageView -> device
        else -> error("Unsupported rendering image view type: ${this::class.qualifiedName}")
    }



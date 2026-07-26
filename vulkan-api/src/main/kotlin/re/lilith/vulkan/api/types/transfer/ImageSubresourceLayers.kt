package re.lilith.vulkan.api.types.transfer

import re.lilith.vulkan.api.types.flags.ImageAspect

data class ImageSubresourceLayers(
    val aspectMask: ImageAspect,
    val mipLevel: Int = 0,
    val baseArrayLayer: Int = 0,
    val layerCount: Int = 1,
) {
    init {
        require(mipLevel >= 0) { "mipLevel must be >= 0." }
        require(baseArrayLayer >= 0) { "baseArrayLayer must be >= 0." }
        require(layerCount > 0) { "layerCount must be > 0." }
    }
}


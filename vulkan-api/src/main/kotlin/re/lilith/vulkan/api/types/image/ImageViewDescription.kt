package re.lilith.vulkan.api.types.image

import re.lilith.vulkan.api.types.enum.ComponentSwizzle
import re.lilith.vulkan.api.types.flags.ImageAspect

data class ComponentMapping(
    val red: ComponentSwizzle = ComponentSwizzle.Identity,
    val green: ComponentSwizzle = ComponentSwizzle.Identity,
    val blue: ComponentSwizzle = ComponentSwizzle.Identity,
    val alpha: ComponentSwizzle = ComponentSwizzle.Identity,
)

data class ImageSubresourceRange(
    val aspectMask: ImageAspect,
    val baseMipLevel: Int = 0,
    val levelCount: Int = 1,
    val baseArrayLayer: Int = 0,
    val layerCount: Int = 1,
)


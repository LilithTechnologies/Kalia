package re.lilith.vulkan.api.memory

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.rendering.RenderingImageView
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.enum.ImageViewType
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.image.ImageSubresourceRange

class ImageView internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val image: Image,
    val config: ImageViewConfig,
) : VulkanResource(), RenderingImageView {
    override fun closeResource() {
        VK10.vkDestroyImageView(device.handle, handle, null)
    }

    companion object {
        fun color2D(
            format: Format,
            mipLevels: Int = 1,
            arrayLayers: Int = 1,
        ): ImageViewConfig = ImageViewConfig(
            type = ImageViewType.TwoDimensional,
            format = format,
            subresourceRange = ImageSubresourceRange(
                aspectMask = ImageAspect.Color,
                baseMipLevel = 0,
                levelCount = mipLevels,
                baseArrayLayer = 0,
                layerCount = arrayLayers,
            ),
        )
    }
}



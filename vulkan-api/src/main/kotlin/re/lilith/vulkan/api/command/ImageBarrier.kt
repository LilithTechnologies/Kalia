package re.lilith.vulkan.api.command

import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.image.ImageSubresourceRange

data class ImageBarrier(
    val image: BarrierImage,
    val oldLayout: ImageLayout,
    val newLayout: ImageLayout,
    val sourceStageMask: PipelineStageMask,
    val destinationStageMask: PipelineStageMask,
    val sourceAccessMask: AccessMask = AccessMask.None,
    val destinationAccessMask: AccessMask = AccessMask.None,
    val subresourceRange: ImageSubresourceRange,
)
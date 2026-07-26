package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.command.ImageBarrier
import re.lilith.vulkan.api.command.pipelineBarrier
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.image.ImageSubresourceRange

fun CommandRecorder.transitionSwapchainImage(
    image: SwapchainImage,
    oldLayout: ImageLayout,
    newLayout: ImageLayout,
): CommandRecorder = apply {
    pipelineBarrier(
        imageBarriers = listOf(
            ImageBarrier(
                image = image,
                oldLayout = oldLayout,
                newLayout = newLayout,
                sourceStageMask = PipelineStageMask.ColorAttachmentOutput,
                destinationStageMask = PipelineStageMask.ColorAttachmentOutput,
                sourceAccessMask = AccessMask.ColorAttachmentWrite,
                destinationAccessMask = AccessMask.ColorAttachmentWrite,
                subresourceRange = ImageSubresourceRange(ImageAspect.Color, levelCount = 1, layerCount = 1),
            ),
        )
    )
}


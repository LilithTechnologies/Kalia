package re.lilith.vulkan.api.command

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkImageBlit
import org.lwjgl.vulkan.VkImageCopy
import re.lilith.vulkan.api.descriptor.Filter
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.geometry.Extent3D
import re.lilith.vulkan.api.types.transfer.BufferImageCopy
import re.lilith.vulkan.api.types.transfer.ImageBlit
import re.lilith.vulkan.api.types.transfer.ImageCopy
import re.lilith.vulkan.api.types.transfer.ImageSubresourceLayers

fun CommandRecorder.copyBufferToImage(
    source: Buffer,
    destination: Image,
    destinationLayout: ImageLayout,
    regions: List<BufferImageCopy>,
): CommandRecorder = apply {
    require(source.device === commandBuffer.device) { "Source buffer must belong to the same logical device as the command buffer." }
    require(destination.device === commandBuffer.device) { "Destination image must belong to the same logical device as the command buffer." }
    require(regions.isNotEmpty()) { "At least one buffer-image copy region is required." }

    MemoryStack.stackPush().use { stack ->
        val copies = VkBufferImageCopy.calloc(regions.size, stack)
        regions.forEachIndexed { index, region ->
            copies[index]
                .bufferOffset(region.bufferOffset)
                .bufferRowLength(region.bufferRowLength)
                .bufferImageHeight(region.bufferImageHeight)
                .imageSubresource { subresource ->
                    subresource.aspectMask(region.imageSubresource.aspectMask.vkBits)
                    subresource.mipLevel(region.imageSubresource.mipLevel)
                    subresource.baseArrayLayer(region.imageSubresource.baseArrayLayer)
                    subresource.layerCount(region.imageSubresource.layerCount)
                }
                .imageOffset { offset ->
                    offset.set(region.imageOffset.x, region.imageOffset.y, region.imageOffset.z)
                }
                .imageExtent { extent ->
                    extent.set(region.imageExtent.width, region.imageExtent.height, region.imageExtent.depth)
                }
        }
        VK10.vkCmdCopyBufferToImage(
            commandBuffer.handle,
            source.handle,
            destination.handle,
            destinationLayout.vkValue,
            copies
        )
    }
}

fun CommandRecorder.copyImage(
    source: BarrierImage,
    sourceLayout: ImageLayout,
    destination: BarrierImage,
    destinationLayout: ImageLayout,
    regions: List<ImageCopy>,
): CommandRecorder = apply {
    require(source.ownerDevice === commandBuffer.device) { "Source image must belong to the same logical device as the command buffer." }
    require(destination.ownerDevice === commandBuffer.device) { "Destination image must belong to the same logical device as the command buffer." }
    require(regions.isNotEmpty()) { "At least one image-copy region is required." }

    MemoryStack.stackPush().use { stack ->
        val copies = VkImageCopy.calloc(regions.size, stack)
        regions.forEachIndexed { index, region ->
            copies[index]
                .srcSubresource { subresource ->
                    subresource.aspectMask(region.sourceSubresource.aspectMask.vkBits)
                    subresource.mipLevel(region.sourceSubresource.mipLevel)
                    subresource.baseArrayLayer(region.sourceSubresource.baseArrayLayer)
                    subresource.layerCount(region.sourceSubresource.layerCount)
                }
                .srcOffset { offset -> offset.set(region.sourceOffset.x, region.sourceOffset.y, region.sourceOffset.z) }
                .dstSubresource { subresource ->
                    subresource.aspectMask(region.destinationSubresource.aspectMask.vkBits)
                    subresource.mipLevel(region.destinationSubresource.mipLevel)
                    subresource.baseArrayLayer(region.destinationSubresource.baseArrayLayer)
                    subresource.layerCount(region.destinationSubresource.layerCount)
                }
                .dstOffset { offset ->
                    offset.set(
                        region.destinationOffset.x,
                        region.destinationOffset.y,
                        region.destinationOffset.z
                    )
                }
                .extent { extent -> extent.set(region.extent.width, region.extent.height, region.extent.depth) }
        }
        VK10.vkCmdCopyImage(
            commandBuffer.handle,
            source.nativeHandle,
            sourceLayout.vkValue,
            destination.nativeHandle,
            destinationLayout.vkValue,
            copies,
        )
    }
}

fun CommandRecorder.blitImage(
    source: BarrierImage,
    sourceLayout: ImageLayout,
    destination: BarrierImage,
    destinationLayout: ImageLayout,
    regions: List<ImageBlit>,
    filter: Filter = Filter.Linear,
): CommandRecorder = apply {
    require(source.ownerDevice === commandBuffer.device) { "Source image must belong to the same logical device as the command buffer." }
    require(destination.ownerDevice === commandBuffer.device) { "Destination image must belong to the same logical device as the command buffer." }
    require(regions.isNotEmpty()) { "At least one image-blit region is required." }

    MemoryStack.stackPush().use { stack ->
        val blits = VkImageBlit.calloc(regions.size, stack)
        regions.forEachIndexed { index, region ->
            blits[index]
                .srcSubresource { subresource ->
                    subresource.aspectMask(region.sourceSubresource.aspectMask.vkBits)
                    subresource.mipLevel(region.sourceSubresource.mipLevel)
                    subresource.baseArrayLayer(region.sourceSubresource.baseArrayLayer)
                    subresource.layerCount(region.sourceSubresource.layerCount)
                }
                .dstSubresource { subresource ->
                    subresource.aspectMask(region.destinationSubresource.aspectMask.vkBits)
                    subresource.mipLevel(region.destinationSubresource.mipLevel)
                    subresource.baseArrayLayer(region.destinationSubresource.baseArrayLayer)
                    subresource.layerCount(region.destinationSubresource.layerCount)
                }
            blits[index].srcOffsets(0)
                .set(region.sourceOffsets.first.x, region.sourceOffsets.first.y, region.sourceOffsets.first.z)
            blits[index].srcOffsets(1)
                .set(region.sourceOffsets.second.x, region.sourceOffsets.second.y, region.sourceOffsets.second.z)
            blits[index].dstOffsets(0).set(
                region.destinationOffsets.first.x,
                region.destinationOffsets.first.y,
                region.destinationOffsets.first.z
            )
            blits[index].dstOffsets(1).set(
                region.destinationOffsets.second.x,
                region.destinationOffsets.second.y,
                region.destinationOffsets.second.z
            )
        }
        VK10.vkCmdBlitImage(
            commandBuffer.handle,
            source.nativeHandle,
            sourceLayout.vkValue,
            destination.nativeHandle,
            destinationLayout.vkValue,
            blits,
            filter.vkValue,
        )
    }
}

fun CommandRecorder.uploadImage(
    source: Buffer,
    destination: Image,
    imageExtent: Extent3D,
    destinationLayout: ImageLayout = ImageLayout.TransferDestinationOptimal,
    imageSubresource: ImageSubresourceLayers = ImageSubresourceLayers(ImageAspect.Color),
): CommandRecorder = copyBufferToImage(
    source = source,
    destination = destination,
    destinationLayout = destinationLayout,
    regions = listOf(
        BufferImageCopy(
            bufferOffset = 0L,
            imageSubresource = imageSubresource,
            imageExtent = imageExtent,
        ),
    ),
)


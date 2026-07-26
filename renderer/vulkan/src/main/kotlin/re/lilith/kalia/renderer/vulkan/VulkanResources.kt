package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.command.*
import re.lilith.vulkan.api.memory.*
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.enum.ImageType
import re.lilith.vulkan.api.types.enum.ImageViewType
import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.ImageUsage
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.geometry.Extent3D
import re.lilith.vulkan.api.types.geometry.Offset3D
import re.lilith.vulkan.api.types.image.ImageSubresourceRange
import re.lilith.vulkan.api.types.transfer.BufferImageCopy
import re.lilith.vulkan.api.types.transfer.ImageBlit
import re.lilith.vulkan.api.types.transfer.ImageSubresourceLayers
import re.lilith.vulkan.api.memory.Buffer as VkBuffer

internal fun VulkanContext.createTextureResources(description: TextureDescription): Pair<Image, ImageView> {
    var usage = ImageUsage.None
    if (description.sampled) usage += ImageUsage.Sampled
    if (description.transferable) usage += ImageUsage.TransferSource + ImageUsage.TransferDestination
    if (description.renderTarget) {
        usage += if (description.format.isColor) ImageUsage.ColorAttachment else ImageUsage.DepthStencilAttachment
    }
    // A sampled texture with mips still needs transfer usage, because mip generation blits between its own levels
    if (description.mipLevels > 1) {
        usage += ImageUsage.TransferSource + ImageUsage.TransferDestination
    }

    val image = allocator.createImage(
        ImageConfig(
            type = ImageType.TwoDimensional,
            format = Convert.format(description.format),
            extent = Extent3D(description.extent.width, description.extent.height, 1),
            mipLevels = description.mipLevels,
            usage = usage,
        ),
        MemoryUsage.GpuOnly,
    )

    val view = device.createImageView(
        image,
        ImageViewConfig(
            type = ImageViewType.TwoDimensional,
            format = Convert.format(description.format),
            subresourceRange = ImageSubresourceRange(
                aspectMask = Convert.aspect(description.format),
                levelCount = description.mipLevels,
                layerCount = 1,
            ),
        ),
    )
    return image to view
}

internal fun VulkanContext.createStagingBuffer(sizeBytes: Long): VkBuffer = allocator.createBuffer(
    BufferConfig(
        size = sizeBytes,
        usage = re.lilith.vulkan.api.types.flags.BufferUsage.TransferSource,
    ),
    MemoryUsage.Upload,
)

/**
 * Records the copy of one staged mip level into [texture]
 */
internal fun CommandRecorder.recordTextureUpload(
    texture: VulkanTexture,
    staging: VkBuffer,
    mipLevel: Int,
    levelExtent: Extent,
    sourceLayout: ImageLayout,
) {
    recordLayoutTransition(texture, sourceLayout, ImageLayout.TransferDestinationOptimal)
    copyBufferToImage(
        source = staging,
        destination = texture.image,
        destinationLayout = ImageLayout.TransferDestinationOptimal,
        regions = listOf(
            BufferImageCopy(
                imageSubresource = ImageSubresourceLayers(
                    aspectMask = Convert.aspect(texture.format),
                    mipLevel = mipLevel,
                ),
                imageExtent = Extent3D(levelExtent.width, levelExtent.height, 1),
            ),
        ),
    )
    recordLayoutTransition(texture, ImageLayout.TransferDestinationOptimal, ImageLayout.ShaderReadOnlyOptimal)
}

/**
 * Records a barrier moving [texture] between two explicit layouts
 */
internal fun CommandRecorder.recordLayoutTransition(
    texture: VulkanTexture,
    from: ImageLayout,
    to: ImageLayout,
) {
    if (from == to) {
        return
    }
    pipelineBarrier(
        listOf(
            ImageBarrier(
                image = texture.image,
                oldLayout = from,
                newLayout = to,
                sourceStageMask = VulkanTexture.stageFor(from),
                destinationStageMask = VulkanTexture.stageFor(to),
                sourceAccessMask = VulkanTexture.accessFor(from),
                destinationAccessMask = VulkanTexture.accessFor(to),
                subresourceRange = texture.subresourceRange,
            ),
        ),
    )
}

/**
 * Records a chain of half-size blits from level 0 down to the last mip
 */
internal fun CommandRecorder.recordMipmapGeneration(texture: VulkanTexture, sourceLayout: ImageLayout) {
    val aspect = Convert.aspect(texture.format)

    // Start from a known layout for the whole image, then step level by level
    recordLayoutTransition(texture, sourceLayout, ImageLayout.TransferDestinationOptimal)

    for (level in 1 until texture.mipLevels) {
        val source = texture.mipExtent(level - 1)
        val destination = texture.mipExtent(level)

        pipelineBarrier(
            listOf(
                ImageBarrier(
                    image = texture.image,
                    oldLayout = ImageLayout.TransferDestinationOptimal,
                    newLayout = ImageLayout.TransferSourceOptimal,
                    sourceStageMask = PipelineStageMask.Transfer,
                    destinationStageMask = PipelineStageMask.Transfer,
                    sourceAccessMask = AccessMask.TransferWrite,
                    destinationAccessMask = AccessMask.TransferRead,
                    subresourceRange = ImageSubresourceRange(aspect, baseMipLevel = level - 1, levelCount = 1),
                ),
            ),
        )

        blitImage(
            source = texture.image,
            sourceLayout = ImageLayout.TransferSourceOptimal,
            destination = texture.image,
            destinationLayout = ImageLayout.TransferDestinationOptimal,
            regions = listOf(
                ImageBlit(
                    sourceSubresource = ImageSubresourceLayers(aspect, mipLevel = level - 1),
                    sourceOffsets = Offset3D() to Offset3D(source.width, source.height, 1),
                    destinationSubresource = ImageSubresourceLayers(aspect, mipLevel = level),
                    destinationOffsets = Offset3D() to Offset3D(destination.width, destination.height, 1),
                ),
            ),
        )
    }

    pipelineBarrier(
        listOf(
            ImageBarrier(
                image = texture.image,
                oldLayout = ImageLayout.TransferSourceOptimal,
                newLayout = ImageLayout.ShaderReadOnlyOptimal,
                sourceStageMask = PipelineStageMask.Transfer,
                destinationStageMask = PipelineStageMask.FragmentShader,
                sourceAccessMask = AccessMask.TransferRead,
                destinationAccessMask = AccessMask.ShaderRead,
                subresourceRange = ImageSubresourceRange(
                    aspect,
                    baseMipLevel = 0,
                    levelCount = (texture.mipLevels - 1).coerceAtLeast(1),
                ),
            ),
            ImageBarrier(
                image = texture.image,
                oldLayout = ImageLayout.TransferDestinationOptimal,
                newLayout = ImageLayout.ShaderReadOnlyOptimal,
                sourceStageMask = PipelineStageMask.Transfer,
                destinationStageMask = PipelineStageMask.FragmentShader,
                sourceAccessMask = AccessMask.TransferWrite,
                destinationAccessMask = AccessMask.ShaderRead,
                subresourceRange = ImageSubresourceRange(
                    aspect,
                    baseMipLevel = texture.mipLevels - 1,
                    levelCount = 1,
                ),
            ),
        ),
    )
}

internal val BufferDescription.needsHostMapping: Boolean
    get() = usage == BufferUsage.STREAM

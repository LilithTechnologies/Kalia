package re.lilith.kalia.renderer.vulkan

import org.lwjgl.vulkan.KHRSwapchain
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.command.ImageBarrier
import re.lilith.vulkan.api.command.blitImage
import re.lilith.vulkan.api.command.pipelineBarrier
import re.lilith.vulkan.api.core.VulkanResultException
import re.lilith.vulkan.api.descriptor.Filter
import re.lilith.vulkan.api.presentation.*
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.geometry.Extent2D
import re.lilith.vulkan.api.types.geometry.Offset3D
import re.lilith.vulkan.api.types.image.ImageSubresourceRange
import re.lilith.vulkan.api.types.transfer.ImageBlit
import re.lilith.vulkan.api.types.transfer.ImageSubresourceLayers

internal class VulkanSwapchain private constructor(
    val swapchain: Swapchain,
    val extent: Extent,
    val format: TextureFormat,
    val backbuffer: VulkanTexture,
    private val renderFinished: List<BinarySemaphore>,
    private val imageLayouts: MutableList<ImageLayout>,
) : AutoCloseable {
    fun acquire(imageAvailable: BinarySemaphore): AcquiredSwapchainImage? = runCatching {
        swapchain.acquireNextImage(semaphore = imageAvailable)
    }.getOrElse { failure ->
        if (failure is VulkanResultException &&
            failure.resultCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
        ) {
            null
        } else {
            throw failure
        }
    }

    fun renderFinishedSemaphore(imageIndex: Int): BinarySemaphore = renderFinished[imageIndex]

    fun recordPresentBlit(recorder: CommandRecorder, acquired: AcquiredSwapchainImage) {
        val target = acquired.image
        val colorRange = ImageSubresourceRange(ImageAspect.Color, levelCount = 1, layerCount = 1)

        if (backbuffer.layout == ImageLayout.Undefined) {
            recorder.pipelineBarrier(
                listOf(
                    ImageBarrier(
                        image = target,
                        oldLayout = imageLayouts[acquired.index],
                        newLayout = ImageLayout.PresentSource,
                        sourceStageMask = PipelineStageMask.TopOfPipe,
                        destinationStageMask = PipelineStageMask.BottomOfPipe,
                        sourceAccessMask = AccessMask.None,
                        destinationAccessMask = AccessMask.None,
                        subresourceRange = colorRange,
                    ),
                ),
            )
            imageLayouts[acquired.index] = ImageLayout.PresentSource
            return
        }

        val barriers = buildList {
            backbuffer.barrierTo(ImageLayout.TransferSourceOptimal)?.let(::add)
            add(
                ImageBarrier(
                    image = target,
                    oldLayout = imageLayouts[acquired.index],
                    newLayout = ImageLayout.TransferDestinationOptimal,
                    sourceStageMask = PipelineStageMask.TopOfPipe,
                    destinationStageMask = PipelineStageMask.Transfer,
                    sourceAccessMask = AccessMask.None,
                    destinationAccessMask = AccessMask.TransferWrite,
                    subresourceRange = colorRange,
                ),
            )
        }
        recorder.pipelineBarrier(barriers)

        recorder.blitImage(
            source = backbuffer.image,
            sourceLayout = ImageLayout.TransferSourceOptimal,
            destination = target,
            destinationLayout = ImageLayout.TransferDestinationOptimal,
            regions = listOf(
                ImageBlit(
                    sourceSubresource = ImageSubresourceLayers(ImageAspect.Color),
                    sourceOffsets = Offset3D() to Offset3D(backbuffer.extent.width, backbuffer.extent.height, 1),
                    destinationSubresource = ImageSubresourceLayers(ImageAspect.Color),
                    destinationOffsets = Offset3D() to Offset3D(extent.width, extent.height, 1),
                ),
            ),
            filter = Filter.Nearest,
        )

        recorder.pipelineBarrier(
            listOf(
                ImageBarrier(
                    image = target,
                    oldLayout = ImageLayout.TransferDestinationOptimal,
                    newLayout = ImageLayout.PresentSource,
                    sourceStageMask = PipelineStageMask.Transfer,
                    destinationStageMask = PipelineStageMask.BottomOfPipe,
                    sourceAccessMask = AccessMask.TransferWrite,
                    destinationAccessMask = AccessMask.None,
                    subresourceRange = colorRange,
                ),
            ),
        )
        imageLayouts[acquired.index] = ImageLayout.PresentSource
    }

    override fun close() {
        backbuffer.close()
        renderFinished.forEach { it.close() }
        swapchain.close()
    }

    companion object {
        fun presentableExtent(context: VulkanContext, requested: Extent): Extent? {
            val capabilities = context.physicalDevice.querySurfaceSupport(context.surface).capabilities
            val current = capabilities.currentExtent
            val width: Int
            val height: Int
            if (current != null) {
                width = current.width
                height = current.height
            } else {
                width = requested.width.coerceIn(
                    capabilities.minimumImageExtent.width,
                    capabilities.maximumImageExtent.width,
                )
                height = requested.height.coerceIn(
                    capabilities.minimumImageExtent.height,
                    capabilities.maximumImageExtent.height,
                )
            }
            return if (width > 0 && height > 0) Extent(width, height) else null
        }

        fun create(
            context: VulkanContext,
            device: VulkanRenderDevice,
            resolved: Extent,
            vsync: Boolean,
            previous: Swapchain? = null,
        ): VulkanSwapchain {

            val swapchain = context.device.createSwapchain(
                surface = context.surface,
                config = SwapchainConfig(
                    extent = Extent2D(resolved.width, resolved.height),
                    imageCount = 3,
                    preferredPresentModes = if (vsync) {
                        listOf(PresentMode.Fifo)
                    } else {
                        listOf(PresentMode.Immediate, PresentMode.Mailbox, PresentMode.Fifo)
                    },
                    queueFamilyIndices = listOf(context.graphicsFamilyIndex, context.presentFamilyIndex).distinct(),
                ),
                oldSwapchain = previous,
            )

            val extent = Extent(swapchain.extent.width, swapchain.extent.height)
            val format = Convert.textureFormat(swapchain.format.format) ?: TextureFormat.BGRA8

            return VulkanSwapchain(
                swapchain = swapchain,
                extent = extent,
                format = format,
                backbuffer = device.createBackbufferTexture(extent, format),
                renderFinished = List(swapchain.images.size) { context.device.createBinarySemaphore() },
                imageLayouts = MutableList(swapchain.images.size) { ImageLayout.Undefined },
            )
        }
    }
}

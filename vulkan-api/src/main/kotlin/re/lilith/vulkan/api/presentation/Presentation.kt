@file:Suppress("DEPRECATION")

package re.lilith.vulkan.api.presentation

import org.lwjgl.sdl.SDLVulkan
import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.device.PhysicalDevice
import re.lilith.vulkan.api.device.Queue
import re.lilith.vulkan.api.device.QueueFamily
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.enum.ImageViewType
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.geometry.Extent2D
import re.lilith.vulkan.api.types.geometry.Offset2D
import re.lilith.vulkan.api.types.geometry.Rect2D

const val SWAPCHAIN_EXTENSION_NAME: String = "VK_KHR_swapchain"

fun VulkanInstance.createSdlSurface(windowHandle: Long): Surface = pushStack { stack ->
    val pointer = stack.mallocLong(1)
    check(SDLVulkan.SDL_Vulkan_CreateSurface(windowHandle, handle, null, pointer)) {
        "Creating SDL Vulkan surface"
    }
    Surface(this, pointer[0])
}

fun PhysicalDevice.querySurfaceSupport(surface: Surface): SurfaceSupport = pushStack { stack ->
    val capabilities = VkSurfaceCapabilitiesKHR.malloc(stack)
    checkVulkanResult(
        KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(handle, surface.handle, capabilities),
        "Querying surface capabilities",
    )

    val formatCount = stack.ints(0)
    checkVulkanResult(
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(handle, surface.handle, formatCount, null),
        "Enumerating surface formats",
    )
    val formats = VkSurfaceFormatKHR.malloc(formatCount[0], stack)
    checkVulkanResult(
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(handle, surface.handle, formatCount, formats),
        "Reading surface formats",
    )

    val presentModeCount = stack.ints(0)
    checkVulkanResult(
        KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(handle, surface.handle, presentModeCount, null),
        "Enumerating present modes",
    )
    val presentModes = stack.mallocInt(presentModeCount[0])
    checkVulkanResult(
        KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(handle, surface.handle, presentModeCount, presentModes),
        "Reading present modes",
    )

    SurfaceSupport(
        capabilities = SurfaceCapabilities(
            minimumImageCount = capabilities.minImageCount(),
            maximumImageCount = capabilities.maxImageCount(),
            currentExtent = capabilities.currentExtent().width().takeIf { it != UInt.MAX_VALUE.toInt() }?.let {
                Extent2D(capabilities.currentExtent().width(), capabilities.currentExtent().height())
            },
            minimumImageExtent = Extent2D(
                capabilities.minImageExtent().width(),
                capabilities.minImageExtent().height()
            ),
            maximumImageExtent = Extent2D(
                capabilities.maxImageExtent().width(),
                capabilities.maxImageExtent().height()
            ),
            currentTransformBits = capabilities.currentTransform(),
            supportedUsageFlags = capabilities.supportedUsageFlags(),
        ),
        formats = List(formats.capacity()) { index ->
            val format = formats[index]
            SurfaceFormat(
                format = Format.entries.firstOrNull { it.vkValue == format.format() } ?: Format.Undefined,
                colorSpace = ColorSpace.entries.firstOrNull { it.vkValue == format.colorSpace() }
                    ?: ColorSpace.SrgbNonLinear,
            )
        },
        presentModes = List(presentModes.capacity()) { index ->
            PresentMode.entries.firstOrNull { it.vkValue == presentModes[index] } ?: PresentMode.Fifo
        },
    )
}

fun PhysicalDevice.findPresentQueueFamily(surface: Surface): QueueFamily? = queueFamilies.firstOrNull { family ->
    pushStack { stack ->
        val supported = stack.ints(VK10.VK_FALSE)
        checkVulkanResult(
            KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(handle, family.index, surface.handle, supported),
            "Querying surface present support",
        )
        supported[0] == VK10.VK_TRUE
    }
}

fun LogicalDevice.createSwapchain(
    surface: Surface,
    config: SwapchainConfig,
    oldSwapchain: Swapchain? = null,
): Swapchain = pushStack { stack ->
    val support = physicalDevice.querySurfaceSupport(surface)
    val surfaceFormat = support.formats.firstOrNull {
        it.format == config.preferredFormat && it.colorSpace == config.preferredColorSpace
    } ?: support.formats.firstOrNull() ?: error("Surface exposes no supported formats.")

    val presentMode = config.preferredPresentModes.firstOrNull { it in support.presentModes } ?: PresentMode.Fifo
    val imageCount = when {
        support.capabilities.maximumImageCount > 0 -> config.imageCount.coerceIn(
            support.capabilities.minimumImageCount,
            support.capabilities.maximumImageCount,
        )

        else -> maxOf(config.imageCount, support.capabilities.minimumImageCount)
    }

    val sharingMode =
        if (config.queueFamilyIndices.distinct().size > 1) VK10.VK_SHARING_MODE_CONCURRENT else VK10.VK_SHARING_MODE_EXCLUSIVE

    val createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
        .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
        .surface(surface.handle)
        .minImageCount(imageCount)
        .imageFormat(surfaceFormat.format.vkValue)
        .imageColorSpace(surfaceFormat.colorSpace.vkValue)
        .imageExtent { it.set(config.extent.width, config.extent.height) }
        .imageArrayLayers(1)
        .imageUsage(
            (VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    and support.capabilities.supportedUsageFlags
        )
        .imageSharingMode(sharingMode)
        .preTransform(support.capabilities.currentTransformBits)
        .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
        .presentMode(presentMode.vkValue)
        .clipped(true)
        .oldSwapchain(oldSwapchain?.handle ?: VK10.VK_NULL_HANDLE)

    if (sharingMode == VK10.VK_SHARING_MODE_CONCURRENT) {
        createInfo.pQueueFamilyIndices(stack.ints(*config.queueFamilyIndices.distinct().toIntArray()))
    }

    val pointer = stack.mallocLong(1)
    checkVulkanResult(KHRSwapchain.vkCreateSwapchainKHR(handle, createInfo, null, pointer), "Creating swapchain")
    val swapchainHandle = pointer[0]

    val imageCountBuffer = stack.ints(0)
    checkVulkanResult(
        KHRSwapchain.vkGetSwapchainImagesKHR(handle, swapchainHandle, imageCountBuffer, null),
        "Enumerating swapchain images"
    )
    val imageHandles = stack.mallocLong(imageCountBuffer[0])
    checkVulkanResult(
        KHRSwapchain.vkGetSwapchainImagesKHR(handle, swapchainHandle, imageCountBuffer, imageHandles),
        "Reading swapchain images"
    )

    val images = List(imageCountBuffer[0]) { index ->
        val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(imageHandles[index])
            .viewType(ImageViewType.TwoDimensional.vkValue)
            .format(surfaceFormat.format.vkValue)
            .subresourceRange { range ->
                range.aspectMask(ImageAspect.Color.vkBits)
                range.baseMipLevel(0)
                range.levelCount(1)
                range.baseArrayLayer(0)
                range.layerCount(1)
            }

        val viewPointer = stack.mallocLong(1)
        checkVulkanResult(
            VK10.vkCreateImageView(handle, viewCreateInfo, null, viewPointer),
            "Creating swapchain image view"
        )
        val view = SwapchainImageView(this, viewPointer[0], index)
        SwapchainImage(this, imageHandles[index], index, view)
    }

    register(Swapchain(this, surface, swapchainHandle, surfaceFormat, config.extent, images))
}

fun Queue.present(
    swapchain: Swapchain,
    imageIndex: Int,
    waitSemaphore: BinarySemaphore,
) {
    pushStack { stack ->
        val presentInfo = VkPresentInfoKHR.calloc(stack)
            .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(stack.longs(waitSemaphore.handle))
            .swapchainCount(1)
            .pSwapchains(stack.longs(swapchain.handle))
            .pImageIndices(stack.ints(imageIndex))

        checkVulkanResult(KHRSwapchain.vkQueuePresentKHR(handle, presentInfo), "Presenting swapchain image")
    }
}

fun Swapchain.renderArea(): Rect2D = Rect2D(Offset2D(), extent)



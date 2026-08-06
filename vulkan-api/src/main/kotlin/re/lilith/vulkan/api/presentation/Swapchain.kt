package re.lilith.vulkan.api.presentation

import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.sync.Fence
import re.lilith.vulkan.api.types.geometry.Extent2D

class Swapchain internal constructor(
    internal val device: LogicalDevice,
    internal val surface: Surface,
    val handle: Long,
    val format: SurfaceFormat,
    val extent: Extent2D,
    val images: List<SwapchainImage>,
) : VulkanResource() {
    fun acquireNextImage(
        semaphore: BinarySemaphore? = null,
        fence: Fence? = null,
        timeoutNanos: Long = Long.MAX_VALUE,
    ): AcquiredSwapchainImage = pushStack { stack ->
        val index = stack.ints(0)
        val result = KHRSwapchain.vkAcquireNextImageKHR(
            device.handle,
            handle,
            timeoutNanos,
            semaphore?.handle ?: VK10.VK_NULL_HANDLE,
            fence?.handle ?: VK10.VK_NULL_HANDLE,
            index,
        )
        if (result != VK10.VK_SUCCESS && result != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            checkVulkanResult(result, "Acquiring next swapchain image")
        }
        val image = images[index[0]]
        AcquiredSwapchainImage(index[0], image, result == KHRSwapchain.VK_SUBOPTIMAL_KHR)
    }

    override fun closeResource() {
        images.asReversed().forEach { image ->
            VK10.vkDestroyImageView(device.handle, image.view.handle, null)
        }
        KHRSwapchain.vkDestroySwapchainKHR(device.handle, handle, null)
    }
}

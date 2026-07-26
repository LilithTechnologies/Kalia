package re.lilith.vulkan.api.interop

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.command.CommandBuffer
import re.lilith.vulkan.api.descriptor.Sampler
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.device.PhysicalDevice
import re.lilith.vulkan.api.device.Queue
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.presentation.Swapchain
import re.lilith.vulkan.api.presentation.SwapchainImage
import re.lilith.vulkan.api.presentation.SwapchainImageView
import re.lilith.vulkan.api.sync.Semaphore
import re.lilith.vulkan.api.types.enum.Format

object RawHandles {
    @JvmStatic
    fun vkInstance(instance: VulkanInstance): VkInstance = instance.handle

    @JvmStatic
    fun vkPhysicalDevice(device: PhysicalDevice): VkPhysicalDevice = device.handle

    @JvmStatic
    fun vkDevice(device: LogicalDevice): VkDevice = device.handle

    @JvmStatic
    fun vkQueue(queue: Queue): VkQueue = queue.handle

    @JvmStatic
    fun vkCommandBuffer(commandBuffer: CommandBuffer): VkCommandBuffer = commandBuffer.handle

    @JvmStatic
    fun swapchain(swapchain: Swapchain): Long = swapchain.handle

    @JvmStatic
    fun image(image: SwapchainImage): Long = image.handle

    @JvmStatic
    fun image(image: Image): Long = image.handle

    @JvmStatic
    fun buffer(buffer: Buffer): Long = buffer.handle

    @JvmStatic
    fun imageView(view: SwapchainImageView): Long = view.handle

    @JvmStatic
    fun imageView(view: ImageView): Long = view.handle

    @JvmStatic
    fun sampler(sampler: Sampler): Long = sampler.handle

    @JvmStatic
    fun semaphore(semaphore: Semaphore): Long = semaphore.handle

    @JvmStatic
    fun format(format: Format): Int = format.vkValue
}

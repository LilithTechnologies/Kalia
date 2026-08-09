package re.lilith.vulkan.api.device

import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkImageViewCreateInfo
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.memory.ImageViewConfig
import re.lilith.vulkan.api.memory.MemoryAllocator
import re.lilith.vulkan.api.qol.pushStack

internal object LogicalDeviceMemory {
    fun createAllocator(device: LogicalDevice): MemoryAllocator = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val instance = device.physicalDevice.instance

        val vulkanFunctions = VmaVulkanFunctions.calloc(stack)
            .set(instance.handle, device.handle)

        val createInfo = VmaAllocatorCreateInfo.calloc(stack)
            .physicalDevice(device.physicalDevice.handle)
            .device(device.handle)
            .instance(instance.handle)
            .pVulkanFunctions(vulkanFunctions)
            .vulkanApiVersion(instance.config.applicationInfo.apiVersion.encoded)

        val pointer = stack.mallocPointer(1)
        checkVulkanResult(Vma.vmaCreateAllocator(createInfo, pointer), "Creating VMA allocator")
        registrar.register(MemoryAllocator(device, pointer[0]))
    }

    fun createImageView(device: LogicalDevice, image: Image, config: ImageViewConfig): ImageView = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        require(image.device === device) { "Image views must be created for images owned by this logical device." }

        val createInfo = VkImageViewCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image.handle)
            .viewType(config.type.vkValue)
            .format(config.format.vkValue)
            .components { mapping ->
                mapping.r(config.components.red.vkValue)
                mapping.g(config.components.green.vkValue)
                mapping.b(config.components.blue.vkValue)
                mapping.a(config.components.alpha.vkValue)
            }
            .subresourceRange { range ->
                range.aspectMask(config.subresourceRange.aspectMask.vkBits)
                range.baseMipLevel(config.subresourceRange.baseMipLevel)
                range.levelCount(config.subresourceRange.levelCount)
                range.baseArrayLayer(config.subresourceRange.baseArrayLayer)
                range.layerCount(config.subresourceRange.layerCount)
            }

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateImageView(device.handle, createInfo, null, pointer), "Creating image view")
        registrar.register(ImageView(device, pointer[0], image, config))
    }
}
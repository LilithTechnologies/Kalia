package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.sync.Fence
import re.lilith.vulkan.api.sync.TimelineSemaphore

internal object LogicalDeviceSynchronization {
    fun createFence(device: LogicalDevice, signaled: Boolean): Fence = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val createInfo = VkFenceCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .flags(if (signaled) VK10.VK_FENCE_CREATE_SIGNALED_BIT else 0)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateFence(device.handle, createInfo, null, pointer), "Creating fence")
        registrar.register(Fence(device, pointer[0]))
    }

    fun createBinarySemaphore(device: LogicalDevice): BinarySemaphore = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val createInfo = VkSemaphoreCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateSemaphore(device.handle, createInfo, null, pointer), "Creating binary semaphore")
        registrar.register(BinarySemaphore(device, pointer[0]))
    }

    fun createTimelineSemaphore(device: LogicalDevice, initialValue: Long): TimelineSemaphore = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val typeInfo = VkSemaphoreTypeCreateInfo.calloc(stack)
            .sType(VK12.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO)
            .semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE)
            .initialValue(initialValue)

        val createInfo = VkSemaphoreCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            .pNext(typeInfo.address())

        val pointer = stack.mallocLong(1)
        checkVulkanResult(
            VK10.vkCreateSemaphore(device.handle, createInfo, null, pointer),
            "Creating timeline semaphore"
        )
        registrar.register(TimelineSemaphore(device, pointer[0]))
    }
}

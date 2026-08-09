package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import re.lilith.vulkan.api.command.CommandPool
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.types.flags.CommandPoolFlags

internal object LogicalDeviceCommands {
    fun createCommandPool(
        device: LogicalDevice,
        queueFamilyIndex: Int,
        flags: CommandPoolFlags,
    ): CommandPool = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val createInfo = VkCommandPoolCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .queueFamilyIndex(queueFamilyIndex)
            .flags(flags.vkBits)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateCommandPool(device.handle, createInfo, null, pointer), "Creating command pool")
        registrar.register(CommandPool(device, pointer[0], queueFamilyIndex, flags))
    }
}



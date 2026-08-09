package re.lilith.vulkan.api.command

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.types.flags.CommandPoolFlags

class CommandPool internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val queueFamilyIndex: Int,
    val flags: CommandPoolFlags,
) : VulkanResource() {
    fun allocate(
        level: CommandBufferLevel = CommandBufferLevel.PRIMARY,
        count: Int = 1,
    ): List<CommandBuffer> = pushStack { stack ->
        require(count > 0) { "count must be > 0" }

        val allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(handle)
            .level(level.vkValue)
            .commandBufferCount(count)

        val pointers = stack.mallocPointer(count)
        checkVulkanResult(
            VK10.vkAllocateCommandBuffers(device.handle, allocateInfo, pointers),
            "Allocating command buffers"
        )

        List(count) { index ->
            own(CommandBuffer(this, VkCommandBuffer(pointers[index], device.handle), level))
        }
    }

    fun allocatePrimary(): CommandBuffer = allocate(CommandBufferLevel.PRIMARY, 1).single()

    fun allocateSecondary(): CommandBuffer = allocate(CommandBufferLevel.SECONDARY, 1).single()

    override fun closeResource() {
        VK10.vkDestroyCommandPool(device.handle, handle, null)
    }
}

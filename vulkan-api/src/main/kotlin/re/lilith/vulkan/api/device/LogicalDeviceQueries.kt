package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkQueryPoolCreateInfo
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.query.QueryPool
import re.lilith.vulkan.api.query.QueryPoolConfig

internal object LogicalDeviceQueries {
    fun createQueryPool(device: LogicalDevice, config: QueryPoolConfig): QueryPool = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val createInfo = VkQueryPoolCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
            .queryType(config.type.vkValue)
            .queryCount(config.capacity)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateQueryPool(device.handle, createInfo, null, pointer), "Creating query pool")
        registrar.register(QueryPool(device, pointer[0], config.capacity))
    }
}

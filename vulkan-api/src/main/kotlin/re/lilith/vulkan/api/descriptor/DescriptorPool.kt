package re.lilith.vulkan.api.descriptor

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.resource.VulkanResource

class DescriptorPool internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val config: DescriptorPoolConfig,
) : VulkanResource() {
    fun reset() {
        checkVulkanResult(VK10.vkResetDescriptorPool(device.handle, handle, 0), "Resetting descriptor pool")
    }

    fun free(descriptorSets: List<DescriptorSet>) {
        if (descriptorSets.isEmpty()) {
            return
        }

        require(config.allowIndividualFree) {
            "Descriptor pool must be created with allowIndividualFree = true to free descriptor sets individually."
        }
        require(descriptorSets.all { it.pool === this }) {
            "All descriptor sets must have been allocated from this descriptor pool."
        }

        MemoryStack.stackPush().use { stack ->
            val setHandles = stack.mallocLong(descriptorSets.size)
            descriptorSets.forEachIndexed { index, descriptorSet -> setHandles.put(index, descriptorSet.handle) }
            checkVulkanResult(
                VK10.vkFreeDescriptorSets(device.handle, handle, setHandles),
                "Freeing descriptor sets",
            )
        }
    }

    override fun closeResource() {
        VK10.vkDestroyDescriptorPool(device.handle, handle, null)
    }
}


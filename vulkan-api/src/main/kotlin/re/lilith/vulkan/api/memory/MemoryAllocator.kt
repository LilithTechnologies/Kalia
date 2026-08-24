package re.lilith.vulkan.api.memory

import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationInfo
import org.lwjgl.util.vma.VmaTotalStatistics
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkImageCreateInfo
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.device.ResourceRegistrar
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.types.enum.SharingMode

/**
 * Thin wrapper around a Vulkan Memory Allocator instance.
 *
 * Replaces manual `vkAllocateMemory` bookkeeping: buffers and images created here own
 * their backing allocation and free it via VMA when closed. The raw [handle] is exposed for native
 * interop (e.g. mods that call into VMA directly).
 */
class MemoryAllocator internal constructor(
    private val device: LogicalDevice,
    val handle: Long,
) : VulkanResource() {

    data class Statistics(
        val blockBytes: Long,
        val allocationBytes: Long,
        val blockCount: Int,
        val allocationCount: Int,
    )

    fun statistics(): Statistics = pushStack { stack ->
        val statistics = VmaTotalStatistics.calloc(stack)
        Vma.vmaCalculateStatistics(handle, statistics)
        val total = statistics.total().statistics()
        Statistics(
            blockBytes = total.blockBytes(),
            allocationBytes = total.allocationBytes(),
            blockCount = total.blockCount(),
            allocationCount = total.allocationCount(),
        )
    }

    fun createBuffer(config: BufferConfig, usage: MemoryUsage): Buffer = pushStack { stack ->
        val createInfo = VkBufferCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(config.size)
            .usage(config.usage.vkBits)
            .sharingMode(config.sharingMode.toVk())

        if (config.sharingMode == SharingMode.Concurrent) {
            require(config.queueFamilyIndices.isNotEmpty()) { "Concurrent buffers require queue family indices." }
            createInfo.pQueueFamilyIndices(stack.ints(*config.queueFamilyIndices.toIntArray()))
        }

        val allocationInfo = VmaAllocationInfo.calloc(stack)
        val pBuffer = stack.mallocLong(1)
        val pAllocation = stack.mallocPointer(1)
        checkVulkanResult(
            Vma.vmaCreateBuffer(handle, createInfo, usage.toCreateInfo(stack), pBuffer, pAllocation, allocationInfo),
            "Creating VMA buffer",
        )

        val mappedAddress = allocationInfo.pMappedData()
        check(!usage.expectsMapped || mappedAddress != 0L) {
            "VMA buffer was expected to be persistently mapped but pMappedData was NULL."
        }

        registrar.register(
            Buffer(
                device = device,
                handle = pBuffer[0],
                config = config,
                allocator = handle,
                allocation = pAllocation[0],
                mappedAddress = mappedAddress,
            ),
        )
    }

    fun createImage(config: ImageConfig, usage: MemoryUsage = MemoryUsage.GpuOnly): Image = pushStack { stack ->
        val createInfo = VkImageCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .flags(config.flags.vkValue)
            .imageType(config.type.vkValue)
            .format(config.format.vkValue)
            .extent { it.set(config.extent.width, config.extent.height, config.extent.depth) }
            .mipLevels(config.mipLevels)
            .arrayLayers(config.arrayLayers)
            .samples(config.samples.vkValue)
            .tiling(config.tiling.vkValue)
            .usage(config.usage.vkBits)
            .sharingMode(config.sharingMode.toVk())
            .initialLayout(config.initialLayout.vkValue)

        if (config.sharingMode == SharingMode.Concurrent) {
            require(config.queueFamilyIndices.isNotEmpty()) { "Concurrent images require queue family indices." }
            createInfo.pQueueFamilyIndices(stack.ints(*config.queueFamilyIndices.toIntArray()))
        }

        val pImage = stack.mallocLong(1)
        val pAllocation = stack.mallocPointer(1)
        checkVulkanResult(
            Vma.vmaCreateImage(handle, createInfo, usage.toCreateInfo(stack), pImage, pAllocation, null),
            "Creating VMA image",
        )

        registrar.register(Image(device, pImage[0], config, handle, pAllocation[0]))
    }

    override fun closeResource() {
        Vma.vmaDestroyAllocator(handle)
    }

    private val registrar: ResourceRegistrar
        get() = device
}

private fun SharingMode.toVk(): Int = when (this) {
    SharingMode.Exclusive -> VK10.VK_SHARING_MODE_EXCLUSIVE
    SharingMode.Concurrent -> VK10.VK_SHARING_MODE_CONCURRENT
}

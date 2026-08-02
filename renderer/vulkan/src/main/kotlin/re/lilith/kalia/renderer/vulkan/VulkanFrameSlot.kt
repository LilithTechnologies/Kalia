package re.lilith.kalia.renderer.vulkan

import re.lilith.vulkan.api.command.CommandBuffer
import re.lilith.vulkan.api.command.CommandPool
import re.lilith.vulkan.api.descriptor.*
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.sync.Fence

internal class VulkanFrameSlot(
    private val device: LogicalDevice,
    val inFlightFence: Fence,
    val imageAvailable: BinarySemaphore,
    val commandBuffer: CommandBuffer,
    val uploadCommandBuffer: CommandBuffer,
    val uploadsFinished: BinarySemaphore,
    private val descriptorPool: DescriptorPool,
    private val transferPool: CommandPool?,
    private val computePool: CommandPool?,
) : AutoCloseable {
    private val transferCommandBuffers = mutableListOf<CommandBuffer>()
    private var nextTransferBuffer = 0

    private val computeCommandBuffers = mutableListOf<CommandBuffer>()
    private var nextComputeBuffer = 0
    private val retired = mutableListOf<AutoCloseable>()

    private val descriptorSets = HashMap<BindingKey, DescriptorSet>()

    var indirectScratch: VulkanBuffer? = null
    var indirectOffset = 0L

    fun retire(resource: AutoCloseable) {
        retired += resource
    }

    fun recycle() {
        indirectOffset = 0L
        descriptorPool.reset()
        descriptorSets.clear()
        retired.forEach { runCatching(it::close) }
        retired.clear()
        nextTransferBuffer = 0
        nextComputeBuffer = 0
    }

    fun nextTransferCommandBuffer(): CommandBuffer? {
        val pool = transferPool ?: return null
        while (transferCommandBuffers.size <= nextTransferBuffer) {
            transferCommandBuffers += pool.allocatePrimary()
        }
        val buffer = transferCommandBuffers[nextTransferBuffer++]
        buffer.reset()
        return buffer
    }

    fun descriptorSet(key: BindingKey, layout: DescriptorSetLayout, write: (DescriptorSet) -> Unit): DescriptorSet {
        val existing = descriptorSets[key]
        if (existing != null) {
            return existing
        }
        re.lilith.kalia.renderer.device.RenderStats.recordDescriptorAllocation()
        val created = device.allocateDescriptorSets(descriptorPool, listOf(layout)).single().also(write)
        descriptorSets[key.copy()] = created
        return created
    }

    fun nextComputeCommandBuffer(graphicsPool: CommandPool): CommandBuffer {
        val pool = computePool ?: graphicsPool
        while (computeCommandBuffers.size <= nextComputeBuffer) {
            computeCommandBuffers += pool.allocatePrimary()
        }
        val buffer = computeCommandBuffers[nextComputeBuffer++]
        buffer.reset()
        return buffer
    }

    override fun close() {
        recycle()
        descriptorPool.close()
        inFlightFence.close()
        imageAvailable.close()
        uploadsFinished.close()
        commandBuffer.close()
        uploadCommandBuffer.close()
        transferCommandBuffers.forEach(CommandBuffer::close)
        transferCommandBuffers.clear()
        computeCommandBuffers.forEach(CommandBuffer::close)
        computeCommandBuffers.clear()
    }

    companion object {
        private const val MAX_SETS_PER_FRAME = 4096

        fun create(context: VulkanContext): VulkanFrameSlot = VulkanFrameSlot(
            device = context.device,
            inFlightFence = context.device.createFence(signaled = true),
            imageAvailable = context.device.createBinarySemaphore(),
            commandBuffer = context.commandPool.allocatePrimary(),
            uploadCommandBuffer = context.commandPool.allocatePrimary(),
            uploadsFinished = context.device.createBinarySemaphore(),
            descriptorPool = context.device.createDescriptorPool(
                DescriptorPoolConfig(
                    maxSets = MAX_SETS_PER_FRAME,
                    poolSizes = listOf(
                        DescriptorPoolSize(DescriptorType.CombinedImageSampler, MAX_SETS_PER_FRAME * 12),
                        DescriptorPoolSize(DescriptorType.UniformBuffer, MAX_SETS_PER_FRAME),
                        DescriptorPoolSize(DescriptorType.UniformBufferDynamic, MAX_SETS_PER_FRAME),
                        DescriptorPoolSize(DescriptorType.StorageBuffer, MAX_SETS_PER_FRAME),
                    ),
                ),
            ),
            transferPool = context.transferCommandPool,
            computePool = context.computeCommandPool,
        )
    }
}

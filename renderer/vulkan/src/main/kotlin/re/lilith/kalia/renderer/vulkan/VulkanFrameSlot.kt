package re.lilith.kalia.renderer.vulkan

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
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
    val hudBoundaryCommandBuffer: CommandBuffer,
    val uploadCommandBuffer: CommandBuffer,
    val presentCommandBuffer: CommandBuffer,
    val uploadsFinished: BinarySemaphore,
    private val descriptorPool: DescriptorPool,
    private val transferPool: CommandPool?,
    private val computePool: CommandPool?,
) : AutoCloseable {
    private val transferCommandBuffers = ObjectArrayList<CommandBuffer>()
    private var nextTransferBuffer = 0

    private val computeCommandBuffers = ObjectArrayList<CommandBuffer>()
    private var nextComputeBuffer = 0
    private val retired = ObjectArrayList<AutoCloseable>()

    private val descriptorSets = Object2ObjectOpenHashMap<BindingKey, DescriptorSet>()
    private var descriptorEpoch = -1

    var indirectScratch: VulkanBuffer? = null
    var indirectOffset = 0L

    fun retire(resource: AutoCloseable) {
        retired += resource
    }

    fun recycle(resourceEpoch: Int = descriptorEpoch) {
        indirectOffset = 0L
        if (descriptorEpoch != resourceEpoch || descriptorSets.size > MAX_CACHED_SETS) {
            descriptorEpoch = resourceEpoch
            descriptorPool.reset()
            descriptorSets.clear()
        }
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
        hudBoundaryCommandBuffer.close()
        uploadCommandBuffer.close()
        presentCommandBuffer.close()
        transferCommandBuffers.forEach(CommandBuffer::close)
        transferCommandBuffers.clear()
        computeCommandBuffers.forEach(CommandBuffer::close)
        computeCommandBuffers.clear()
    }

    companion object {
        private const val MAX_SETS_PER_FRAME = 4096

        private const val MAX_CACHED_SETS = MAX_SETS_PER_FRAME / 2

        fun create(context: VulkanContext): VulkanFrameSlot = VulkanFrameSlot(
            device = context.device,
            inFlightFence = context.device.createFence(signaled = true),
            imageAvailable = context.device.createBinarySemaphore(),
            commandBuffer = context.commandPool.allocatePrimary(),
            hudBoundaryCommandBuffer = context.commandPool.allocatePrimary(),
            uploadCommandBuffer = context.commandPool.allocatePrimary(),
            presentCommandBuffer = context.commandPool.allocatePrimary(),
            uploadsFinished = context.device.createBinarySemaphore(),
            descriptorPool = context.device.createDescriptorPool(
                DescriptorPoolConfig(
                    maxSets = MAX_SETS_PER_FRAME,
                    poolSizes = buildList {
                        add(DescriptorPoolSize(DescriptorType.CombinedImageSampler, MAX_SETS_PER_FRAME * 12))
                        add(DescriptorPoolSize(DescriptorType.UniformBuffer, MAX_SETS_PER_FRAME))
                        add(DescriptorPoolSize(DescriptorType.UniformBufferDynamic, MAX_SETS_PER_FRAME))
                        add(DescriptorPoolSize(DescriptorType.StorageBuffer, MAX_SETS_PER_FRAME))
                        // Asking for a descriptor type the device does not
                        // implement is invalid, so this only appears once the
                        // ray tracing extensions are actually enabled.
                        if (context.device.config.features.rayQuery) {
                            add(DescriptorPoolSize(DescriptorType.AccelerationStructure, MAX_SETS_PER_FRAME))
                        }
                    },
                ),
            ),
            transferPool = context.transferCommandPool,
            computePool = context.computeCommandPool,
        )
    }
}

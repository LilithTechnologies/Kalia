package re.lilith.kalia.renderer.vulkan

import re.lilith.vulkan.api.command.CommandBuffer
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
) : AutoCloseable {
    private val retired = mutableListOf<AutoCloseable>()

    private val descriptorSets = HashMap<Any, DescriptorSet>()

    fun retire(resource: AutoCloseable) {
        retired += resource
    }

    fun recycle() {
        descriptorPool.reset()
        descriptorSets.clear()
        retired.forEach { runCatching(it::close) }
        retired.clear()
    }

    fun descriptorSet(key: Any, layout: DescriptorSetLayout, write: (DescriptorSet) -> Unit): DescriptorSet =
        descriptorSets.getOrPut(key) {
            device.allocateDescriptorSets(descriptorPool, listOf(layout)).single().also(write)
        }

    override fun close() {
        recycle()
        descriptorPool.close()
        inFlightFence.close()
        imageAvailable.close()
        uploadsFinished.close()
        commandBuffer.close()
        uploadCommandBuffer.close()
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
                        DescriptorPoolSize(DescriptorType.CombinedImageSampler, MAX_SETS_PER_FRAME * 4),
                        DescriptorPoolSize(DescriptorType.UniformBuffer, MAX_SETS_PER_FRAME),
                        DescriptorPoolSize(DescriptorType.StorageBuffer, MAX_SETS_PER_FRAME),
                    ),
                ),
            ),
        )
    }
}

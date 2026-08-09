package re.lilith.vulkan.api.command

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPushDescriptor
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkImageMemoryBarrier
import re.lilith.vulkan.api.descriptor.DescriptorSet
import re.lilith.vulkan.api.descriptor.PUSH_DESCRIPTOR_EXTENSION_NAME
import re.lilith.vulkan.api.descriptor.PushDescriptorWrite
import re.lilith.vulkan.api.descriptor.encodeDescriptorWrites
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.pipeline.PipelineBindPoint
import re.lilith.vulkan.api.pipeline.PipelineLayout
import re.lilith.vulkan.api.pipeline.ShaderStageFlags
import re.lilith.vulkan.api.presentation.SwapchainImage
import re.lilith.vulkan.api.types.flags.DependencyFlags
import re.lilith.vulkan.api.types.flags.PipelineStageMask

fun CommandRecorder.bindDescriptorSets(
    pipelineLayout: PipelineLayout,
    descriptorSets: List<DescriptorSet>,
    firstSet: Int = 0,
    bindPoint: PipelineBindPoint = PipelineBindPoint.Graphics,
    dynamicOffsets: List<Int> = emptyList(),
): CommandRecorder = apply {
    for (descriptorSet in descriptorSets) {
        require(descriptorSet.device === commandBuffer.device) { "Descriptor sets must belong to the same logical device as the command buffer." }
    }
    require(pipelineLayout.device === commandBuffer.device) { "Pipeline layout must belong to the same logical device as the command buffer." }
    require(firstSet >= 0) { "firstSet must be >= 0." }
    for (dynamicOffset in dynamicOffsets) {
        require(dynamicOffset >= 0) { "dynamicOffsets must all be >= 0." }
    }
    val layouts = pipelineLayout.config.descriptorSetLayouts
    require(firstSet + descriptorSets.size <= layouts.size) {
        "Pipeline layout does not expose enough descriptor set layouts to bind ${descriptorSets.size} set(s) starting at set $firstSet."
    }
    for (index in descriptorSets.indices) {
        require(!layouts[firstSet + index].isPushDescriptor) {
            "Push-descriptor set layouts must be populated with pushDescriptorSet instead of bindDescriptorSets."
        }
    }

    MemoryStack.stackPush().use { stack ->
        val setHandles = stack.mallocLong(descriptorSets.size)
        for (index in descriptorSets.indices) {
            setHandles.put(index, descriptorSets[index].handle)
        }
        val offsets =
            if (dynamicOffsets.isEmpty()) {
                null
            } else {
                stack.mallocInt(dynamicOffsets.size).also { buffer ->
                    for (index in dynamicOffsets.indices) {
                        buffer.put(index, dynamicOffsets[index])
                    }
                }
            }
        VK10.vkCmdBindDescriptorSets(
            commandBuffer.handle,
            bindPoint.vkValue,
            pipelineLayout.handle,
            firstSet,
            setHandles,
            offsets,
        )
    }
}

fun CommandRecorder.pushDescriptorSet(
    pipelineLayout: PipelineLayout,
    setIndex: Int,
    writes: List<PushDescriptorWrite>,
    bindPoint: PipelineBindPoint = PipelineBindPoint.Graphics,
): CommandRecorder = apply {
    require(PUSH_DESCRIPTOR_EXTENSION_NAME in commandBuffer.device.enabledExtensions) {
        "Push descriptors require enabling $PUSH_DESCRIPTOR_EXTENSION_NAME on the logical device."
    }
    require(pipelineLayout.device === commandBuffer.device) { "Pipeline layout must belong to the same logical device as the command buffer." }
    require(setIndex >= 0) { "setIndex must be >= 0." }
    require(writes.isNotEmpty()) { "At least one push-descriptor write is required." }

    val descriptorSetLayout = pipelineLayout.config.descriptorSetLayouts.getOrNull(setIndex)
        ?: error("Pipeline layout does not define descriptor set layout index $setIndex.")
    require(descriptorSetLayout.isPushDescriptor) {
        "Pipeline layout set $setIndex is not configured as a push-descriptor set layout."
    }

    MemoryStack.stackPush().use { stack ->
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
            commandBuffer.handle,
            bindPoint.vkValue,
            pipelineLayout.handle,
            setIndex,
            encodeDescriptorWrites(stack, commandBuffer.device, writes),
        )
    }
}

fun CommandRecorder.pushConstants(
    pipelineLayout: PipelineLayout,
    stageFlags: ShaderStageFlags,
    data: ByteArray,
    offset: Int = 0,
): CommandRecorder = apply {
    require(pipelineLayout.device === commandBuffer.device) { "Pipeline layout must belong to the same logical device as the command buffer." }
    require(data.isNotEmpty()) { "Push-constant data must not be empty." }

    MemoryStack.stackPush().use { stack ->
        val bytes = stack.malloc(data.size)
        bytes.put(0, data)
        VK10.vkCmdPushConstants(commandBuffer.handle, pipelineLayout.handle, stageFlags.vkBits, offset, bytes)
    }
}

fun CommandRecorder.pipelineBarrier(
    imageBarriers: List<ImageBarrier>,
    dependencyFlags: DependencyFlags = DependencyFlags.None,
): CommandRecorder = apply {
    if (imageBarriers.isEmpty()) {
        return@apply
    }

    require(imageBarriers.all { it.image.ownerDevice === commandBuffer.device }) {
        "All barrier images must belong to the same logical device as the command buffer."
    }

    MemoryStack.stackPush().use { stack ->
        val vkBarriers = VkImageMemoryBarrier.calloc(imageBarriers.size, stack)
        var sourceStages = PipelineStageMask.None
        var destinationStages = PipelineStageMask.None

        imageBarriers.forEachIndexed { index, barrier ->
            sourceStages += barrier.sourceStageMask
            destinationStages += barrier.destinationStageMask
            vkBarriers[index]
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(barrier.oldLayout.vkValue)
                .newLayout(barrier.newLayout.vkValue)
                .srcAccessMask(barrier.sourceAccessMask.vkBits)
                .dstAccessMask(barrier.destinationAccessMask.vkBits)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(barrier.image.nativeHandle)
                .subresourceRange { range ->
                    range.aspectMask(barrier.subresourceRange.aspectMask.vkBits)
                    range.baseMipLevel(barrier.subresourceRange.baseMipLevel)
                    range.levelCount(barrier.subresourceRange.levelCount)
                    range.baseArrayLayer(barrier.subresourceRange.baseArrayLayer)
                    range.layerCount(barrier.subresourceRange.layerCount)
                }
        }

        VK10.vkCmdPipelineBarrier(
            commandBuffer.handle,
            sourceStages.vkBits,
            destinationStages.vkBits,
            dependencyFlags.vkBits,
            null,
            null,
            vkBarriers,
        )
    }
}

internal val BarrierImage.nativeHandle: Long
    get() = when (this) {
        is Image -> handle
        is SwapchainImage -> handle
        else -> error("Unsupported barrier image type: ${this::class.qualifiedName}")
    }

internal val BarrierImage.ownerDevice: LogicalDevice
    get() = when (this) {
        is Image -> device
        is SwapchainImage -> device
        else -> error("Unsupported barrier image type: ${this::class.qualifiedName}")
    }


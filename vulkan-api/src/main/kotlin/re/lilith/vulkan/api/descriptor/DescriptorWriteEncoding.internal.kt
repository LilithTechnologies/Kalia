package re.lilith.vulkan.api.descriptor

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRAccelerationStructure
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR
import re.lilith.vulkan.api.device.LogicalDevice

internal fun encodeDescriptorWrites(
    stack: MemoryStack,
    device: LogicalDevice,
    writes: List<DescriptorWrite>,
): VkWriteDescriptorSet.Buffer {
    val vkWrites = VkWriteDescriptorSet.calloc(writes.size, stack)
    writes.forEachIndexed { index, write ->
        val vkWrite = vkWrites[index]
            .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstBinding(write.binding)
            .dstArrayElement(write.arrayElement)
            .descriptorType(write.descriptorType.vkValue)

        if (write is DescriptorSetWrite) {
            require(write.targetSet.device === device) { "Descriptor set must belong to this logical device." }
            vkWrite.dstSet(write.targetSet.handle)
        }

        when (write) {
            is BufferDescriptorWrite -> {
                require(write.descriptors.all { it.buffer.device === device }) {
                    "All descriptor buffers must belong to this logical device."
                }
                val bufferInfos = VkDescriptorBufferInfo.calloc(write.descriptors.size, stack)
                write.descriptors.forEachIndexed { descriptorIndex, descriptor ->
                    bufferInfos[descriptorIndex]
                        .buffer(descriptor.buffer.handle)
                        .offset(descriptor.offset)
                        .range(descriptor.range ?: VK10.VK_WHOLE_SIZE)
                }
                vkWrite.descriptorCount(write.descriptors.size).pBufferInfo(bufferInfos)
            }

            is ImageDescriptorWrite -> {
                require(write.descriptors.all { it.imageView.device === device }) {
                    "All descriptor image views must belong to this logical device."
                }
                require(write.descriptors.all { it.sampler == null || it.sampler.device === device }) {
                    "All descriptor samplers must belong to this logical device."
                }
                if (write.descriptorType == DescriptorType.CombinedImageSampler) {
                    require(write.descriptors.all { it.sampler != null }) {
                        "Combined-image-sampler writes require a sampler for each descriptor."
                    }
                }
                val imageInfos = VkDescriptorImageInfo.calloc(write.descriptors.size, stack)
                write.descriptors.forEachIndexed { descriptorIndex, descriptor ->
                    imageInfos[descriptorIndex]
                        .imageView(descriptor.imageView.handle)
                        .imageLayout(descriptor.imageLayout.vkValue)
                        .sampler(descriptor.sampler?.handle ?: VK10.VK_NULL_HANDLE)
                }
                vkWrite.descriptorCount(write.descriptors.size).pImageInfo(imageInfos)
            }

            is AccelerationStructureDescriptorWrite -> {
                require(write.structures.all { it.device === device }) {
                    "All acceleration structures must belong to this logical device."
                }
                // Acceleration structures are not described by pBufferInfo or
                // pImageInfo; the handles ride along in a chained struct and only
                // descriptorCount is set on the write itself.
                val handles = stack.mallocLong(write.structures.size)
                write.structures.forEachIndexed { handleIndex, structure ->
                    handles.put(handleIndex, structure.handle)
                }
                val chained = VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                    .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
                    .pAccelerationStructures(handles)
                vkWrite.descriptorCount(write.structures.size).pNext(chained.address())
            }
        }
    }
    return vkWrites
}


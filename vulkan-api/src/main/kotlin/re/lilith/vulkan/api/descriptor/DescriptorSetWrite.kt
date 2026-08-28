package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.accel.AccelerationStructure

sealed interface DescriptorSetWrite : DescriptorWrite {
    val targetSet: DescriptorSet

    data class AccelerationStructureWrite(
        override val targetSet: DescriptorSet,
        override val binding: Int,
        override val structures: List<AccelerationStructure>,
        override val arrayElement: Int = 0,
    ) : DescriptorSetWrite, AccelerationStructureDescriptorWrite {
        override val descriptorType: DescriptorType = DescriptorType.AccelerationStructure

        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(structures.isNotEmpty()) { "At least one acceleration structure is required." }
        }
    }

    data class BufferWrite(
        override val targetSet: DescriptorSet,
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptors: List<BufferDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : DescriptorSetWrite, BufferDescriptorWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one buffer descriptor is required." }
        }
    }

    data class ImageWrite(
        override val targetSet: DescriptorSet,
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptors: List<ImageDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : DescriptorSetWrite, ImageDescriptorWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one image descriptor is required." }
        }
    }
}


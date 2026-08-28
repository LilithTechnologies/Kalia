package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.accel.AccelerationStructure

sealed interface PushDescriptorWrite : DescriptorWrite {
    data class AccelerationStructureWrite(
        override val binding: Int,
        override val structures: List<AccelerationStructure>,
        override val arrayElement: Int = 0,
    ) : PushDescriptorWrite, AccelerationStructureDescriptorWrite {
        override val descriptorType: DescriptorType = DescriptorType.AccelerationStructure

        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(structures.isNotEmpty()) { "At least one acceleration structure is required." }
        }
    }

    data class BufferWrite(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptors: List<BufferDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : PushDescriptorWrite, BufferDescriptorWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one buffer descriptor is required." }
        }
    }

    data class ImageWrite(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptors: List<ImageDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : PushDescriptorWrite, ImageDescriptorWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one image descriptor is required." }
        }
    }
}


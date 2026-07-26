package re.lilith.vulkan.api.descriptor

sealed interface PushDescriptorWrite : DescriptorWrite {
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


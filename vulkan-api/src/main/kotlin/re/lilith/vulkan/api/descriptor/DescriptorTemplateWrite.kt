package re.lilith.vulkan.api.descriptor

sealed interface DescriptorTemplateWrite {
    val binding: Int
    val arrayElement: Int
    val descriptorType: DescriptorType

    data class BufferWrite(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        val descriptors: List<BufferDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : DescriptorTemplateWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one buffer descriptor is required." }
        }
    }

    data class ImageWrite(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        val descriptors: List<ImageDescriptorInfo>,
        override val arrayElement: Int = 0,
    ) : DescriptorTemplateWrite {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptors.isNotEmpty()) { "At least one image descriptor is required." }
        }
    }
}


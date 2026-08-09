package re.lilith.vulkan.api.descriptor

sealed interface DescriptorUpdateTemplateEntry {
    val binding: Int
    val arrayElement: Int
    val descriptorType: DescriptorType
    val descriptorCount: Int

    data class BufferEntry(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptorCount: Int = 1,
        override val arrayElement: Int = 0,
    ) : DescriptorUpdateTemplateEntry {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptorCount > 0) { "descriptorCount must be > 0." }
        }
    }

    data class ImageEntry(
        override val binding: Int,
        override val descriptorType: DescriptorType,
        override val descriptorCount: Int = 1,
        override val arrayElement: Int = 0,
    ) : DescriptorUpdateTemplateEntry {
        init {
            require(binding >= 0) { "binding must be >= 0." }
            require(arrayElement >= 0) { "arrayElement must be >= 0." }
            require(descriptorCount > 0) { "descriptorCount must be > 0." }
        }
    }
}


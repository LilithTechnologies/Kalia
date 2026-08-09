package re.lilith.vulkan.api.descriptor

sealed interface DescriptorWrite {
    val binding: Int
    val arrayElement: Int
    val descriptorType: DescriptorType
}

internal sealed interface BufferDescriptorWrite : DescriptorWrite {
    val descriptors: List<BufferDescriptorInfo>
}

internal sealed interface ImageDescriptorWrite : DescriptorWrite {
    val descriptors: List<ImageDescriptorInfo>
}


package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.accel.AccelerationStructure

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

internal sealed interface AccelerationStructureDescriptorWrite : DescriptorWrite {
    val structures: List<AccelerationStructure>
}


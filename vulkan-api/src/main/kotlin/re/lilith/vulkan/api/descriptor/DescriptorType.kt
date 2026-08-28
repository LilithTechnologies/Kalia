package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.KHRAccelerationStructure
import org.lwjgl.vulkan.VK10

enum class DescriptorType(internal val vkValue: Int) {
    Sampler(VK10.VK_DESCRIPTOR_TYPE_SAMPLER),
    CombinedImageSampler(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
    SampledImage(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE),
    StorageImage(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE),
    UniformTexelBuffer(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER),
    StorageTexelBuffer(VK10.VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER),
    UniformBuffer(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
    StorageBuffer(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER),
    UniformBufferDynamic(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC),
    StorageBufferDynamic(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC),
    InputAttachment(VK10.VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT),
    AccelerationStructure(KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR),
}

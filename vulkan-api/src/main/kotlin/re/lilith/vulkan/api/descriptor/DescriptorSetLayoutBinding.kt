package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.pipeline.ShaderStageFlags

data class DescriptorSetLayoutBinding(
    val binding: Int,
    val descriptorType: DescriptorType,
    val descriptorCount: Int = 1,
    val stageFlags: ShaderStageFlags,
    val bindingFlags: DescriptorBindingFlags = DescriptorBindingFlags.None,
) {
    init {
        require(binding >= 0) { "binding must be >= 0." }
        require(descriptorCount > 0) { "descriptorCount must be > 0." }
    }
}
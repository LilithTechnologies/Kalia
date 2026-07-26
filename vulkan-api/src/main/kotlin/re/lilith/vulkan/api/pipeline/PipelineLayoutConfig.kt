package re.lilith.vulkan.api.pipeline

import re.lilith.vulkan.api.descriptor.DescriptorSetLayout

data class PipelineLayoutConfig(
    val descriptorSetLayouts: List<DescriptorSetLayout> = emptyList(),
    val pushConstantRanges: List<PushConstantRange> = emptyList(),
)
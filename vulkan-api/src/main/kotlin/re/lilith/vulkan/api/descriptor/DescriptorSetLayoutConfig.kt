package re.lilith.vulkan.api.descriptor

data class DescriptorSetLayoutConfig(
    val bindings: List<DescriptorSetLayoutBinding> = emptyList(),
    val isPushDescriptor: Boolean = false,
    val allowUpdateAfterBindPool: Boolean = false,
)


package re.lilith.vulkan.api.descriptor

data class DescriptorSetAllocation(
    val layout: DescriptorSetLayout,
    val variableDescriptorCount: Int? = null,
) {
    init {
        require(variableDescriptorCount == null || variableDescriptorCount >= 0) {
            "variableDescriptorCount must be >= 0 when specified."
        }
    }
}


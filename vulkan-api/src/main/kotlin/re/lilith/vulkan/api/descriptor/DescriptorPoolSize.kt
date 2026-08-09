package re.lilith.vulkan.api.descriptor

data class DescriptorPoolSize(
    val descriptorType: DescriptorType,
    val descriptorCount: Int,
) {
    init {
        require(descriptorCount > 0) { "descriptorCount must be > 0." }
    }
}
package re.lilith.vulkan.api.descriptor

data class DescriptorPoolConfig(
    val maxSets: Int,
    val poolSizes: List<DescriptorPoolSize>,
    val allowIndividualFree: Boolean = false,
    val allowUpdateAfterBind: Boolean = false,
) {
    init {
        require(maxSets > 0) { "maxSets must be > 0." }
        require(poolSizes.isNotEmpty()) { "At least one descriptor pool size is required." }
    }
}
package re.lilith.vulkan.api.pipeline

data class SpecializationMapEntry(
    val constantId: Int,
    val offset: Int,
    val size: Int,
) {
    init {
        require(constantId >= 0) { "constantId must be >= 0." }
        require(offset >= 0) { "offset must be >= 0." }
        require(size > 0) { "size must be > 0." }
    }
}


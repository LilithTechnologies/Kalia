package re.lilith.vulkan.api.pipeline

data class SpecializationInfo(
    val data: ByteArray,
    val mapEntries: List<SpecializationMapEntry>,
) {
    init {
        require(data.isNotEmpty()) { "Specialization data must not be empty." }
        require(mapEntries.isNotEmpty()) { "At least one specialization map entry is required." }
    }

    companion object {
        fun build(configure: SpecializationInfoBuilder.() -> Unit): SpecializationInfo =
            SpecializationInfoBuilder().apply(configure).build()
    }
}


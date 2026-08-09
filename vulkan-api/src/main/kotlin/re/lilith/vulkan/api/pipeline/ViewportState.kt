package re.lilith.vulkan.api.pipeline

data class ViewportState(
    val viewportCount: Int = 1,
    val scissorCount: Int = 1,
) {
    init {
        require(viewportCount > 0) { "viewportCount must be > 0." }
        require(scissorCount > 0) { "scissorCount must be > 0." }
    }
}


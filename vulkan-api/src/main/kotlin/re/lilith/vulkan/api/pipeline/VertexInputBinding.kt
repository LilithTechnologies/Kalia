package re.lilith.vulkan.api.pipeline

data class VertexInputBinding(
    val binding: Int,
    val stride: Int,
    val inputRate: VertexInputRate = VertexInputRate.Vertex,
) {
    init {
        require(binding >= 0) { "binding must be >= 0." }
        require(stride >= 0) { "stride must be >= 0." }
    }
}


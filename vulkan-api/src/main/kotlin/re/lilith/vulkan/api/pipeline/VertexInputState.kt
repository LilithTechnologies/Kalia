package re.lilith.vulkan.api.pipeline

data class VertexInputState(
    val bindings: List<VertexInputBinding> = emptyList(),
    val attributes: List<VertexInputAttribute> = emptyList(),
) {
    init {
        require(bindings.distinctBy(VertexInputBinding::binding).size == bindings.size) {
            "Vertex-input bindings must use unique binding indices."
        }
        require(attributes.distinctBy(VertexInputAttribute::location).size == attributes.size) {
            "Vertex-input attributes must use unique locations."
        }
        require(attributes.all { attribute -> bindings.any { binding -> binding.binding == attribute.binding } }) {
            "Each vertex-input attribute must reference an existing binding."
        }
    }
}


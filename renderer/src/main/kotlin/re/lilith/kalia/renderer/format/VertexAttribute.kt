package re.lilith.kalia.renderer.format

/**
 * One attribute inside a vertex.
 *
 * [location] is the shader input location. Kalia does not reflect shaders, so the layout
 * declared here is the single source of truth and must match the shader by construction.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class VertexAttribute(
    val name: String,
    val location: Int,
    val format: VertexAttributeFormat,
    val offset: Int,
) {
    init {
        require(location >= 0) { "Vertex attribute location must be >= 0." }
        require(offset >= 0) { "Vertex attribute offset must be >= 0." }
    }
}
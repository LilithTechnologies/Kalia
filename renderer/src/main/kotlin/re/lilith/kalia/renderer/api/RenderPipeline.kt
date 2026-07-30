package re.lilith.kalia.renderer.api

import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.shader.ShaderProgram

data class RenderPipeline(
    val name: String,
) {
    class Builder(
        val program: ShaderProgram,
    ) {
        private lateinit var vertexFormat: VertexFormat

        fun vertexFormat(vertexFormat: VertexFormat) = apply { this.vertexFormat = vertexFormat }
        fun build(): RenderPipeline {
            TODO()
        }
    }
}
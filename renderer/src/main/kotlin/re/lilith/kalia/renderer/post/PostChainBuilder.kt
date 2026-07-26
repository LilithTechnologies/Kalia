package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.graph.RenderGraphDsl
import re.lilith.kalia.renderer.shader.ShaderProgram

@RenderGraphDsl
class PostChainBuilder internal constructor() {
    internal val stages = mutableListOf<PostStage>()

    /**
     * Adds a stage.
     */
    fun stage(
        name: String,
        program: ShaderProgram,
        scale: Float = 1f,
        format: TextureFormat = TextureFormat.RGBA16F,
        build: PostStageBuilder.() -> Unit = {},
    ) {
        stages += PostStageBuilder(name, program, scale, format).apply(build).build()
    }
}

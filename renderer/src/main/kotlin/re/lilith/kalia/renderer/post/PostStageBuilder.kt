package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.graph.RenderGraphDsl
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.shader.ShaderProgram

@RenderGraphDsl
class PostStageBuilder internal constructor(
    private val name: String,
    private val program: ShaderProgram,
    private val scale: Float,
    private val format: TextureFormat,
) {
    private val extraInputs = mutableListOf<TextureHandle>()
    private val params = FloatArray(PostStage.PARAM_FLOATS)
    private var paramCount = 0
    private var blend: BlendState = BlendState.OPAQUE
    private var sampler: SamplerDescription = SamplerDescription.LINEAR_CLAMP

    /**
     * Binds an extra texture
     */
    fun input(handle: TextureHandle) {
        extraInputs += handle
    }

    /**
     * Overrides how the input textures are sampled. Defaults to bilinear with clamped edges
     */
    fun sampling(description: SamplerDescription) {
        sampler = description
    }

    /**
     * Blends the stage output over the target instead of replacing it
     */
    fun blending(state: BlendState) {
        blend = state
    }

    /**
     * Writes the stage's push constants.
     */
    fun params(write: ParamWriter.() -> Unit) {
        val writer = ParamWriter(params, paramCount)
        writer.write()
        paramCount = writer.cursor
    }

    internal fun build() = PostStage(
        name = name,
        program = program,
        scale = scale,
        format = format,
        extraInputs = extraInputs.toList(),
        params = params.copyOf(),
        blend = blend,
        sampler = sampler,
    )
}

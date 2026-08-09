package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.shader.ShaderProgram

internal class PostStage(
    val name: String,
    val program: ShaderProgram,
    val scale: Float,
    val format: TextureFormat,
    val extraInputs: List<TextureHandle>,
    val params: FloatArray,
    val blend: BlendState,
    val sampler: SamplerDescription,
) {
    companion object {
        const val PARAM_FLOATS: Int = 24
    }
}
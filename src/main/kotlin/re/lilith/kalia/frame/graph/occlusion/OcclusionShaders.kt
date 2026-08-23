package re.lilith.kalia.frame.graph.occlusion

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object OcclusionShaders {
    private var cached: ShaderProgram? = null

    fun program(): ShaderProgram = cached ?: ShaderProgram(
        label = "kalia/occlusion",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl(
                "occlusion.vert",
                ShaderAssets.assemble("kalia:occlusion.vert", emptyList()),
            ),
            ShaderStage.FRAGMENT to ShaderSource.Glsl(
                "occlusion.frag",
                ShaderAssets.assemble("kalia:occlusion.frag", emptyList()),
            ),
        ),
        bindings = listOf(
            ShaderBinding(
                name = "KaliaScene",
                binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                kind = BindingKind.UNIFORM_BUFFER_DYNAMIC,
                stages = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT),
            ),
        ),
        pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
    ).also { cached = it }
}

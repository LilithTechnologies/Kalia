package re.lilith.kalia.ui

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude
import kotlin.collections.component1
import kotlin.collections.component2

object CubeMapShaders {
    private var cached: ShaderProgram? = null

    fun program(): ShaderProgram = cached ?: ShaderProgram(
        label = "kalia/panorama",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("panorama.vert", ShaderAssets.assemble("kalia:panorama.vert", emptyList())),
            ShaderStage.FRAGMENT to ShaderSource.Glsl("panorama.frag", ShaderAssets.assemble("kalia:panorama.frag", emptyList())),
        ),
        bindings = listOf(
            ShaderBinding(
                name = "kaliaBaseTexture",
                binding = ShaderPrelude.Bindings.BASE_TEXTURE,
                kind = BindingKind.TEXTURE,
                stages = setOf(ShaderStage.FRAGMENT),
            ),
            ShaderBinding(
                name = "KaliaScene",
                binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                kind = BindingKind.UNIFORM_BUFFER,
                stages = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT),
            ),
        ),
        pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
    ).apply {
        stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), 0) }
    }.also { cached = it }
}
package re.lilith.kalia.entity.particle

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object ParticleShaders {
    private var cached: ShaderProgram? = null

    fun program(): ShaderProgram = cached ?: ShaderProgram(
        label = "kalia/particle",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("particle.vert", ShaderAssets.assemble("kalia:particle.vert", emptyList())),
            ShaderStage.FRAGMENT to ShaderSource.Glsl("particle.frag", ShaderAssets.assemble("kalia:particle.frag", emptyList())),
        ),
        bindings = listOf(
            ShaderBinding(
                name = "kaliaBaseTexture",
                binding = ShaderPrelude.Bindings.BASE_TEXTURE,
                kind = BindingKind.TEXTURE,
                stages = setOf(ShaderStage.FRAGMENT),
            ),
            ShaderBinding(
                name = "kaliaLightmapTexture",
                binding = ShaderPrelude.Bindings.LIGHTMAP_TEXTURE,
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

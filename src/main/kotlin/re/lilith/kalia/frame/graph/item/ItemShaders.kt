package re.lilith.kalia.frame.graph.item

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object ItemShaders {
    private var cached: ShaderProgram? = null

    fun program(): ShaderProgram = cached ?: ShaderProgram(
        label = "kalia/item",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("item.vert", ShaderAssets.assemble("kalia:item.vert", emptyList())),
            ShaderStage.FRAGMENT to ShaderSource.Glsl("item.frag", ShaderAssets.assemble("kalia:item.frag", emptyList())),
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
                kind = BindingKind.UNIFORM_BUFFER_DYNAMIC,
                stages = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT),
            ),
        ),
        pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
    ).apply {
        stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), 0) }
    }.also { cached = it }
}

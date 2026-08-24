package re.lilith.kalia.frame.graph.entity.nametag

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object NametagShaders {
    private var cached: ShaderProgram? = null
    private var cachedBindless = false

    fun program(bindless: Boolean = false): ShaderProgram {
        cached?.takeIf { cachedBindless == bindless }?.let { return it }
        val defines = if (bindless) listOf("BINDLESS") else emptyList()
        return ShaderProgram(
            label = if (bindless) "kalia/nametag-bindless" else "kalia/nametag",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("nametag.vert", ShaderAssets.assemble("kalia:nametag.vert", defines)),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("nametag.frag", ShaderAssets.assemble("kalia:nametag.frag", defines)),
            ),
            bindings = buildList {
                if (!bindless) {
                    add(
                        ShaderBinding(
                            name = "kaliaBaseTexture",
                            binding = ShaderPrelude.Bindings.BASE_TEXTURE,
                            kind = BindingKind.TEXTURE,
                            stages = setOf(ShaderStage.FRAGMENT),
                        ),
                    )
                }
                add(
                    ShaderBinding(
                        name = "KaliaScene",
                        binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                        kind = BindingKind.UNIFORM_BUFFER_DYNAMIC,
                        stages = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT),
                    ),
                )
            },
            pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
        ).apply {
            stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), 0) }
        }.also {
            cached = it
            cachedBindless = bindless
        }
    }
}

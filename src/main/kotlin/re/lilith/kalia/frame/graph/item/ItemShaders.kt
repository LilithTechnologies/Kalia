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
    private val programs = HashMap<Boolean, ShaderProgram>()

    fun program(bindless: Boolean = false): ShaderProgram = programs.getOrPut(bindless) {
        val defines = if (bindless) listOf("BINDLESS") else emptyList()
        ShaderProgram(
            label = if (bindless) "kalia/item-bindless" else "kalia/item",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl(
                    "item.vert",
                    ShaderAssets.assemble("kalia:item.vert", defines),
                ),
                ShaderStage.FRAGMENT to ShaderSource.Glsl(
                    "item.frag",
                    ShaderAssets.assemble("kalia:item.frag", defines),
                ),
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
                        name = "kaliaLightmapTexture",
                        binding = ShaderPrelude.Bindings.LIGHTMAP_TEXTURE,
                        kind = BindingKind.TEXTURE,
                        stages = setOf(ShaderStage.FRAGMENT),
                    ),
                )
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
            stages.forEach { (stage, source) ->
                ShaderAssets.dump(source, stage.name.lowercase(), if (bindless) 1 else 0)
            }
        }
    }
}

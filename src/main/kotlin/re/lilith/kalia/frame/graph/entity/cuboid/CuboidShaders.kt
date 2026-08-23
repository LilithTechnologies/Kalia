package re.lilith.kalia.frame.graph.entity.cuboid

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object CuboidShaders {
    private val programs = HashMap<String, ShaderProgram>()

    private const val BINDLESS_KEY = "bindless"

    fun programFor(textureArray: Boolean, bindless: Boolean = false): ShaderProgram =
        programs.getOrPut(if (bindless) BINDLESS_KEY else textureArray.toString()) {
        val defines = when {
            bindless -> listOf("BINDLESS")
            textureArray -> listOf("TEXTURE_ARRAY")
            else -> emptyList()
        }
        val key = when {
            bindless -> "cuboid-bindless"
            textureArray -> "cuboid-array"
            else -> "cuboid"
        }
        ShaderProgram(
            label = "kalia/$key",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("$key.vert", ShaderAssets.assemble("kalia:cuboid.vert", defines)),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("$key.frag", ShaderAssets.assemble("kalia:cuboid.frag", defines)),
            ),
            bindings = listOfNotNull(
                if (bindless) null else ShaderBinding(
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
            stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), if (bindless) 2 else if (textureArray) 1 else 0) }
        }
    }
}

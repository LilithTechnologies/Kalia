package re.lilith.kalia.entity.cuboid

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object CuboidShaders {
    private val programs = HashMap<Boolean, ShaderProgram>()

    fun programFor(textureArray: Boolean): ShaderProgram = programs.getOrPut(textureArray) {
        val defines = if (textureArray) listOf("TEXTURE_ARRAY") else emptyList()
        val key = if (textureArray) "cuboid-array" else "cuboid"
        ShaderProgram(
            label = "kalia/$key",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("$key.vert", ShaderAssets.assemble("kalia:cuboid.vert", defines)),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("$key.frag", ShaderAssets.assemble("kalia:cuboid.frag", defines)),
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
            stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), if (textureArray) 1 else 0) }
        }
    }
}

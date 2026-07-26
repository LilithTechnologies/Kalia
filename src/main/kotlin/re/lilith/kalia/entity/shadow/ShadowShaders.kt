package re.lilith.kalia.entity.shadow

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import re.lilith.kalia.shader.ShaderPrelude

object ShadowShaders {
    val program: ShaderProgram by lazy {
        ShaderProgram(
            label = "kalia/shadow",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("shadow.vert", ShaderAssets.assemble("shadow.vert")),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("shadow.frag", ShaderAssets.assemble("shadow.frag")),
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
                    stages = setOf(ShaderStage.VERTEX),
                ),
            ),
            pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
        ).apply {
            stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), 0) }
        }
    }
}

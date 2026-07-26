package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.shader.*

object PostEffects {
    const val PUSH_CONSTANT_BLOCK: String = """
layout(push_constant) uniform KaliaPost {
    vec4 kaliaParams[6];
    vec2 kaliaInputTexel;
    vec2 kaliaOutputSize;
};
"""

    const val FULLSCREEN_VERTEX: String = """
#version 450
layout(location = 0) out vec2 uv;
void main() {
    uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, 0.0, 1.0);
}
"""

    fun program(
        label: String,
        fragment: String,
        extraTextures: List<String> = emptyList(),
    ): ShaderProgram = ShaderProgram(
        label = label,
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("$label.vert", FULLSCREEN_VERTEX),
            ShaderStage.FRAGMENT to ShaderSource.Glsl("$label.frag", fragment),
        ),
        bindings = buildList {
            add(ShaderBinding("kaliaInput", 0, BindingKind.TEXTURE, setOf(ShaderStage.FRAGMENT)))
            extraTextures.forEachIndexed { index, name ->
                add(ShaderBinding(name, index + 1, BindingKind.TEXTURE, setOf(ShaderStage.FRAGMENT)))
            }
        },
        pushConstantBytes = PostStage.PARAM_FLOATS * 4 + 16,
    )

    val blit: ShaderProgram = program(
        label = "kalia/blit",
        fragment = """
#version 450
layout(binding = 0) uniform sampler2D kaliaInput;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;
$PUSH_CONSTANT_BLOCK
void main() {
    fragColor = texture(kaliaInput, uv);
}
""",
    )
}

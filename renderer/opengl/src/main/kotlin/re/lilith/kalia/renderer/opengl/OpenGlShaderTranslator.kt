package re.lilith.kalia.renderer.opengl

import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage

// janky opengl shader translator
internal object OpenGlShaderTranslator {
    const val PUSH_CONSTANT_BINDING = 15

    class Translated(val code: String, val pushConstantBlock: String?)

    private val VERSION = Regex("""^\s*#version\s+450\b.*$""", RegexOption.MULTILINE)
    private val SET_QUALIFIER = Regex("""set\s*=\s*\d+\s*,\s*""")
    private val PUSH_CONSTANT = Regex("""layout\s*\(\s*push_constant\s*\)\s*uniform\s+(\w+)""")
    private val BINDING_THEN_COMMA = Regex("""binding\s*=\s*\d+\s*,\s*""")
    private val COMMA_THEN_BINDING = Regex(""",\s*binding\s*=\s*\d+""")
    private val LONE_BINDING_LAYOUT = Regex("""layout\s*\(\s*binding\s*=\s*\d+\s*\)\s*""")
    private val MAIN = Regex("""void\s+main\s*\(\s*\)""")

    fun translate(stage: ShaderStage, source: ShaderSource, flipY: Boolean): Translated {
        val glsl = when (source) {
            is ShaderSource.Glsl -> source
            is ShaderSource.SpirV -> error(
                "The OpenGL backend only accepts GLSL shader sources.",
            )
        }

        var code = glsl.code
        code = VERSION.replaceFirst(code, "#version 410 core")
        code = SET_QUALIFIER.replace(code, "")

        var pushBlock: String? = null
        code = PUSH_CONSTANT.replace(code) { match ->
            pushBlock = match.groupValues[1]
            "layout(std140) uniform ${match.groupValues[1]}"
        }

        code = BINDING_THEN_COMMA.replace(code, "")
        code = COMMA_THEN_BINDING.replace(code, "")
        code = LONE_BINDING_LAYOUT.replace(code, "")

        code = code.replace(Regex("""\bgl_VertexIndex\b"""), "gl_VertexID")
        code = code.replace(Regex("""\bgl_InstanceIndex\b"""), "gl_InstanceID")

        if (flipY && stage == ShaderStage.VERTEX) {
            code = MAIN.replaceFirst(code, "void kalia_main()")
            code += """

void main() {
    kalia_main();
    gl_Position.y = -gl_Position.y;
    gl_Position.z = 2.0 * gl_Position.z - gl_Position.w;
}
"""
        }

        return Translated(code, pushBlock)
    }
}

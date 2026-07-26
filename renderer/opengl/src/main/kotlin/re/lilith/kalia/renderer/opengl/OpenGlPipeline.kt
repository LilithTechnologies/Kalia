package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.glGetInteger
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.glDeleteVertexArrays
import org.lwjgl.opengl.GL30C.glGenVertexArrays
import org.lwjgl.opengl.GL31C.*
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderStage

internal class OpenGlPipeline(
    private val owner: OpenGlRenderDevice,
    override val label: String,
    val description: GraphicsPipelineDescription,
    val program: Int,
    val vao: Int,
) : GpuPipeline {
    private var closed = false

    override val isClosed: Boolean get() = closed

    val pushConstantBytes: Int get() = description.program.pushConstantBytes

    override fun close() {
        if (closed) return
        closed = true
        val programId = program
        val vaoId = vao
        owner.scheduleRelease {
            glDeleteProgram(programId)
            glDeleteVertexArrays(vaoId)
        }
    }

    companion object {
        fun create(owner: OpenGlRenderDevice, description: GraphicsPipelineDescription): OpenGlPipeline {
            val shaderProgram = description.program
            val flipY = !owner.context.supportsClipControl

            val pushBlocks = LinkedHashSet<String>()
            val shaders = shaderProgram.stages.map { (stage, source) ->
                val translated = OpenGlShaderTranslator.translate(stage, source, flipY)
                translated.pushConstantBlock?.let(pushBlocks::add)
                compileShader(shaderProgram.label, stage, translated.code)
            }

            val program = glCreateProgram()
            try {
                shaders.forEach { glAttachShader(program, it) }
                glLinkProgram(program)
                if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
                    error("Linking '${shaderProgram.label}' failed: ${glGetProgramInfoLog(program)}")
                }
            } catch (failure: Throwable) {
                glDeleteProgram(program)
                shaders.forEach(::glDeleteShader)
                throw failure
            }
            shaders.forEach(::glDeleteShader)

            val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
            glUseProgram(program)
            try {
                for (binding in shaderProgram.bindings) {
                    when (binding.kind) {
                        BindingKind.TEXTURE -> {
                            val location = glGetUniformLocation(program, binding.name)
                            if (location >= 0) {
                                glUniform1i(location, binding.binding)
                            }
                        }

                        BindingKind.UNIFORM_BUFFER -> {
                            val index = glGetUniformBlockIndex(program, binding.name)
                            if (index != GL_INVALID_INDEX) {
                                glUniformBlockBinding(program, index, binding.binding)
                            }
                        }

                        BindingKind.STORAGE_BUFFER -> error(
                            "Program '${shaderProgram.label}' binds storage buffer '${binding.name}', which is unsupported by the OpenGL backend.",
                        )
                    }
                }
                for (blockName in pushBlocks) {
                    val index = glGetUniformBlockIndex(program, blockName)
                    if (index != GL_INVALID_INDEX) {
                        glUniformBlockBinding(program, index, OpenGlShaderTranslator.PUSH_CONSTANT_BINDING)
                    }
                }
            } finally {
                glUseProgram(previousProgram)
            }

            return OpenGlPipeline(
                owner = owner,
                label = shaderProgram.label,
                description = description,
                program = program,
                vao = glGenVertexArrays(),
            )
        }

        private fun compileShader(label: String, stage: ShaderStage, code: String): Int {
            val type = when (stage) {
                ShaderStage.VERTEX -> GL_VERTEX_SHADER
                ShaderStage.FRAGMENT -> GL_FRAGMENT_SHADER
                ShaderStage.COMPUTE -> error("Program '$label' has a compute stage, which is unsupported by the OpenGL backend.")
            }
            val shader = glCreateShader(type)
            glShaderSource(shader, code)
            glCompileShader(shader)
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
                val log = glGetShaderInfoLog(shader)
                glDeleteShader(shader)
                error("Compiling the ${stage.name.lowercase()} stage of '$label' failed: $log")
            }
            return shader
        }
    }
}

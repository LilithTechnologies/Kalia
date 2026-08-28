package re.lilith.kalia.tests

import org.lwjgl.util.shaderc.Shaderc
import org.embeddedt.embeddium.impl.gpu.shader.ShaderConstants
import org.embeddedt.embeddium.impl.gpu.shader.ShaderParser
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader
import re.lilith.kalia.frame.graph.rt.RayTracingShaders
import re.lilith.kalia.shader.ShaderAssets
import kotlin.test.Test

class RayTracingShaderTests {
    private fun compile(name: String, source: String, kind: Int = Shaderc.shaderc_glsl_fragment_shader) {
        val compiler = Shaderc.shaderc_compiler_initialize()
        val options = Shaderc.shaderc_compile_options_initialize()
        try {
            Shaderc.shaderc_compile_options_set_target_env(
                options,
                Shaderc.shaderc_target_env_vulkan,
                Shaderc.shaderc_env_version_vulkan_1_2,
            )
            val result = Shaderc.shaderc_compile_into_spv(
                compiler, source, kind, name, "main", options,
            )
            val status = Shaderc.shaderc_result_get_compilation_status(result)
            val message = Shaderc.shaderc_result_get_error_message(result)
            Shaderc.shaderc_result_release(result)
            check(status == Shaderc.shaderc_compilation_status_success) { "$name:\n$message" }
            println("OK $name")
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }

    /**
     * The chunk shaders gain a variant that writes a geometry buffer instead of a
     * lit image. It goes through Sodium's own preprocessor rather than Kalia's, so
     * it is built here the same way the renderer builds it.
     */
    @Test
    fun `terrain geometry buffer variant compiles`() {
        val constants = ShaderConstants.builder()
            .add("KALIA_GBUFFER")
            .add("USE_FRAGMENT_DISCARD")
            .build()

        for (type in listOf("vsh" to Shaderc.shaderc_glsl_vertex_shader, "fsh" to Shaderc.shaderc_glsl_fragment_shader)) {
            val source = ShaderParser.parseShader(
                ShaderLoader.getShaderSource("sodium:blocks/block_layer_opaque.${type.first}"),
                ShaderLoader::getShaderSource,
                constants,
            )
            compile("block_layer_opaque.${type.first}", source, type.second)
        }
    }

    /**
     * Builds every ray tracing program, which runs the check that each one declares
     * every descriptor its shader reads.
     *
     * Kalia does not reflect shaders, so a shader reading a binding the program did
     * not declare produces a pipeline layout that does not describe it. That is
     * undefined behaviour rather than an error, and has been seen to fault inside
     * the driver during pipeline creation with nothing useful to point at.
     */
    @Test
    fun `ray tracing programs declare every binding they read`() {
        for (program in listOf(
            RayTracingShaders.PRIMARY,
            RayTracingShaders.TRACE,
            RayTracingShaders.TEMPORAL,
            RayTracingShaders.ATROUS,
            RayTracingShaders.LIGHTING,
            RayTracingShaders.FALLBACK,
            RayTracingShaders.TRANSMITTANCE,
            RayTracingShaders.SKY,
        )) {
            println("OK bindings ${program.label}")
        }
    }

    @Test
    fun `ray tracing shaders compile`() {
        compile("rt_trace", ShaderAssets.assemble("kalia:rt/rt_trace.frag", version = ShaderAssets.RAY_TRACING_VERSION))
        compile("rt_temporal", ShaderAssets.assemble("kalia:rt/rt_temporal.frag"))
        compile("rt_atrous", ShaderAssets.assemble("kalia:rt/rt_atrous.frag"))
        compile("rt_lighting", ShaderAssets.assemble("kalia:rt/rt_lighting.frag"))
        compile("rt_fallback", ShaderAssets.assemble("kalia:rt/rt_fallback.frag"))
        compile("rt_translut", ShaderAssets.assemble("kalia:rt/rt_translut.frag"))
        compile("rt_sky", ShaderAssets.assemble("kalia:rt/rt_sky.frag"))
        compile(
            "rt_primary",
            ShaderAssets.assemble("kalia:rt/rt_primary.frag", version = ShaderAssets.RAY_TRACING_VERSION),
        )
    }
}

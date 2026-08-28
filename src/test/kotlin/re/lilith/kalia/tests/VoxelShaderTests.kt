package re.lilith.kalia.tests

import org.lwjgl.util.shaderc.Shaderc
import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.shader.ShaderAssets
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compiles every voxel shader with the same toolchain the Vulkan backend uses.
 *
 * Shader bugs otherwise only surface the first time a player turns the feature on, which is a
 * miserable way to find a typo in a `#define`. Running shaderc here catches them at build time.
 */
class VoxelShaderTests {

    @Test
    fun `voxel fragment shaders compile`() {
        for (name in FRAGMENT_SHADERS) {
            compile(name, ShaderAssets.assemble("kalia:$name"), Shaderc.shaderc_fragment_shader)
        }
    }

    @Test
    fun `fullscreen vertex stage compiles`() {
        compile("fullscreen.vert", PostEffects.FULLSCREEN_VERTEX, Shaderc.shaderc_vertex_shader)
    }

    @Test
    fun `brick header layout matches the shader`() {
        val source = ShaderAssets.assemble("kalia:svo_common.glsl")
        for ((macro, expected) in EXPECTED_OFFSETS) {
            val pattern = Regex("""#define\s+$macro\s+(\d+)u""")
            val match = pattern.find(source) ?: fail("svo_common.glsl does not define $macro")
            val actual = match.groupValues[1].toInt()
            assertTrue(
                actual == expected,
                "$macro is $actual in GLSL but $expected in VoxelFormat; the brick layout has drifted.",
            )
        }
    }

    private fun compile(name: String, source: String, kind: Int) {
        val compiler = Shaderc.shaderc_compiler_initialize()
        check(compiler != 0L) { "Could not initialise shaderc." }
        val options = Shaderc.shaderc_compile_options_initialize()
        try {
            Shaderc.shaderc_compile_options_set_target_env(
                options,
                Shaderc.shaderc_target_env_vulkan,
                Shaderc.shaderc_env_version_vulkan_1_2,
            )
            val result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main", options)
            check(result != 0L) { "shaderc returned no result for '$name'." }
            try {
                val status = Shaderc.shaderc_result_get_compilation_status(result)
                if (status != Shaderc.shaderc_compilation_status_success) {
                    fail("$name failed to compile:\n${Shaderc.shaderc_result_get_error_message(result)}")
                }
            } finally {
                Shaderc.shaderc_result_release(result)
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }

    private companion object {
        val FRAGMENT_SHADERS = listOf(
            "svo_trace.frag",
            "svo_primary.frag",
            "svo_temporal.frag",
            "svo_denoise.frag",
            
        )

        /** Mirrors the constants in `re.lilith.kalia.voxel.VoxelFormat`. */
        val EXPECTED_OFFSETS = mapOf(
            "SVO_COARSE_OFFSET" to re.lilith.kalia.voxel.VoxelFormat.COARSE_OFFSET,
            "SVO_OCCUPANCY_OFFSET" to re.lilith.kalia.voxel.VoxelFormat.OCCUPANCY_OFFSET,
            "SVO_PREFIX_OFFSET" to re.lilith.kalia.voxel.VoxelFormat.PREFIX_OFFSET,
            "SVO_PALETTE_HEADER_OFFSET" to re.lilith.kalia.voxel.VoxelFormat.PALETTE_HEADER_OFFSET,
            "SVO_PALETTE_OFFSET" to re.lilith.kalia.voxel.VoxelFormat.PALETTE_OFFSET,
        )
    }
}

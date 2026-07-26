package re.lilith.kalia.renderer.vulkan

import org.lwjgl.util.shaderc.Shaderc
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a [ShaderSource] into SPIR-V
 */
internal object VulkanShaderCompiler {
    private val memoryCache = ConcurrentHashMap<String, ByteArray>()

    private val cacheDirectory = Paths.get(
        System.getProperty("kalia.cacheDir") ?: ".kalia",
        "shaders",
    )

    fun compile(stage: ShaderStage, source: ShaderSource): ByteArray = when (source) {
        is ShaderSource.SpirV -> source.words
        is ShaderSource.Glsl -> memoryCache.computeIfAbsent(cacheKey(stage, source)) { key ->
            readDiskCache(key) ?: compileGlsl(stage, source).also { writeDiskCache(key, it) }
        }
    }

    private fun cacheKey(stage: ShaderStage, source: ShaderSource.Glsl): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(stage.name.toByteArray())
        digest.update(source.code.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readDiskCache(key: String): ByteArray? = runCatching {
        val file = cacheDirectory.resolve("$key.spv")
        if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
    }.getOrNull()

    private fun writeDiskCache(key: String, spirv: ByteArray) {
        runCatching {
            Files.createDirectories(cacheDirectory)
            Files.write(cacheDirectory.resolve("$key.spv"), spirv)
        }
    }

    private fun compileGlsl(stage: ShaderStage, source: ShaderSource.Glsl): ByteArray {
        val compiler = Shaderc.shaderc_compiler_initialize()
        check(compiler != 0L) { "Could not initialise shaderc." }
        val options = Shaderc.shaderc_compile_options_initialize()
        try {
            Shaderc.shaderc_compile_options_set_target_env(
                options,
                Shaderc.shaderc_target_env_vulkan,
                Shaderc.shaderc_env_version_vulkan_1_2,
            )
            Shaderc.shaderc_compile_options_set_optimization_level(
                options,
                Shaderc.shaderc_optimization_level_performance,
            )

            val result = Shaderc.shaderc_compile_into_spv(
                compiler,
                source.code,
                shadercKind(stage),
                source.name,
                "main",
                options,
            )
            check(result != 0L) { "shaderc returned no result for '${source.name}'." }

            try {
                val status = Shaderc.shaderc_result_get_compilation_status(result)
                check(status == Shaderc.shaderc_compilation_status_success) {
                    "Failed to compile '${source.name}':\n" +
                            Shaderc.shaderc_result_get_error_message(result)
                }
                val bytes = Shaderc.shaderc_result_get_bytes(result)
                    ?: error("shaderc produced no bytes for '${source.name}'.")
                return ByteArray(bytes.remaining()).also(bytes::get)
            } finally {
                Shaderc.shaderc_result_release(result)
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }

    private fun shadercKind(stage: ShaderStage): Int = when (stage) {
        ShaderStage.VERTEX -> Shaderc.shaderc_vertex_shader
        ShaderStage.FRAGMENT -> Shaderc.shaderc_fragment_shader
        ShaderStage.COMPUTE -> Shaderc.shaderc_compute_shader
    }
}

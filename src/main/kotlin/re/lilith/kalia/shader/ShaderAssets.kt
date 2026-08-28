package re.lilith.kalia.shader

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import re.lilith.kalia.renderer.shader.ShaderSource

object ShaderAssets {
    private val dumpDirectory = Paths.get(".kalia", "shaders", "source").apply { Files.createDirectories(this) }

    /**
     * The version every shader compiles against unless it asks for more.
     */
    const val DEFAULT_VERSION: Int = 450

    /**
     * Ray tracing builtins are only added to the symbol table at 460, so a shader
     * that declares a `rayQueryEXT` has to ask for that version even though the
     * extension itself is enabled explicitly.
     */
    const val RAY_TRACING_VERSION: Int = 460

    @JvmStatic
    @JvmOverloads
    fun assemble(
        fileName: String,
        defines: List<String> = emptyList(),
        version: Int = DEFAULT_VERSION,
    ): String = buildString {
        appendLine("#version $version")
        defines.forEach { appendLine("#define $it") }
        append(resolveIncludes(readAsset(fileName)))
    }

    fun dump(source: ShaderSource, stageName: String, signature: Int) {
        when (source) {
            is ShaderSource.Glsl ->
                dumpDirectory.resolve("kalia-generated-${source.name}-$stageName.glsl").writeText(source.code)

            is ShaderSource.SpirV ->
                dumpDirectory.resolve("kalia-generated-$signature-$stageName.spv").writeBytes(source.words)
        }
    }

    private fun readAsset(name: String): String {
        val (namespace, path) = if (':' in name) {
            name.substringBefore(':') to name.substringAfter(':')
        } else {
            "kalia" to name
        }

        val stream = ShaderAssets::class.java
            .getResourceAsStream("/assets/$namespace/shaders/$path")
            ?: error("Missing shader asset 'assets/$namespace/shaders/$path'.")

        return stream.use { it.readBytes().decodeToString() }
    }

    private val includePattern = Regex("""#include\s+"kalia:([\w./-]+)"""")

    private fun resolveIncludes(source: String, seen: MutableSet<String> = mutableSetOf()): String =
        includePattern.replace(source) { match ->
            val name = match.groupValues[1]
            if (seen.add(name)) {
                resolveIncludes(readAsset(name), seen)
            } else {
                ""
            }
        }
}

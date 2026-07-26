package re.lilith.kalia.shader

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import re.lilith.kalia.renderer.shader.ShaderSource

object ShaderAssets {
    private val dumpDirectory = Paths.get(".kalia", "shaders", "source").apply { Files.createDirectories(this) }

    fun assemble(fileName: String, defines: List<String> = emptyList()): String = buildString {
        appendLine("#version 450")
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
        val stream = ShaderAssets::class.java.getResourceAsStream("/assets/kalia/shaders/$name")
            ?: error("Missing shader asset 'assets/kalia/shaders/$name'.")
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

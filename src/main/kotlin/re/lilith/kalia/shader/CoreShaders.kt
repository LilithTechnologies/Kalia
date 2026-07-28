package re.lilith.kalia.shader

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.shader.*
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations

object CoreShaders {
    private const val TEXGEN_BIT = 1 shl 8
    private const val INSTANCED_BIT = 1 shl 9
    private const val TEXTURE_ARRAY_BIT = 1 shl 10

    private val programs = Int2ObjectOpenHashMap<ShaderProgram>()

    fun programFor(format: TranslatedVertexFormat, texGen: Boolean = false): ShaderProgram {
        val signature = signature(format) or (if (texGen) TEXGEN_BIT else 0)
        return programs.getOrPut(signature) {
            val key = if (texGen) "${format.shaderKey}-texgen" else format.shaderKey
            build(
                label = "kalia/core/$key",
                key = key,
                file = "core",
                format = format,
                texGen = texGen,
                signature = signature,
            )
        }
    }

    fun instancedProgramFor(format: TranslatedVertexFormat, textureArray: Boolean = false): ShaderProgram {
        val signature = signature(format) or INSTANCED_BIT or (if (textureArray) TEXTURE_ARRAY_BIT else 0)
        val suffix = if (textureArray) "-instanced-array" else "-instanced"
        return programs.getOrPut(signature) {
            build(
                label = "kalia/instanced/${format.shaderKey}${if (textureArray) "-array" else ""}",
                key = "${format.shaderKey}$suffix",
                file = "instanced",
                format = format,
                texGen = false,
                signature = signature,
                textureArray = textureArray,
            )
        }
    }

    private fun build(
        label: String,
        key: String,
        file: String,
        format: TranslatedVertexFormat,
        texGen: Boolean,
        signature: Int,
        textureArray: Boolean = false,
    ): ShaderProgram {
        val defines = buildList {
            if (format.hasColor) add("HAS_COLOR")
            if (format.hasTexture) add("HAS_TEXTURE")
            if (format.hasLightmap) add("HAS_LIGHTMAP")
            if (lightmapKind(format) == LightmapKind.SIGNED_SHORT) add("LIGHTMAP_SIGNED_SHORT")
            if (format.hasNormal) add("HAS_NORMAL")
            if (texGen) add("TEXGEN")
            if (textureArray) add("TEXTURE_ARRAY")
        }
        return ShaderProgram(
            label = label,
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("$key.vert", ShaderAssets.assemble("kalia:$file.vert", defines)),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("$key.frag", ShaderAssets.assemble("kalia:$file.frag", defines)),
            ),
            bindings = buildList {
                if (format.hasTexture || texGen) {
                    add(
                        ShaderBinding(
                            name = "kaliaBaseTexture",
                            binding = ShaderPrelude.Bindings.BASE_TEXTURE,
                            kind = BindingKind.TEXTURE,
                            stages = setOf(ShaderStage.FRAGMENT),
                        ),
                    )
                }
                add(
                    ShaderBinding(
                        name = "kaliaLightmapTexture",
                        binding = ShaderPrelude.Bindings.LIGHTMAP_TEXTURE,
                        kind = BindingKind.TEXTURE,
                        stages = setOf(ShaderStage.FRAGMENT),
                    ),
                )
                add(
                    ShaderBinding(
                        name = "KaliaScene",
                        binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                        kind = BindingKind.UNIFORM_BUFFER,
                        stages = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT),
                    ),
                )
            },
            pushConstantBytes = ShaderUniforms.PUSH_CONSTANT_BYTES,
        ).apply {
            stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), signature) }
        }
    }

    private fun signature(format: TranslatedVertexFormat): Int {
        var bits = 0
        if (format.hasColor) bits = bits or 1
        if (format.hasTexture) bits = bits or 2
        if (format.hasLightmap) bits = bits or 4
        if (format.hasNormal) bits = bits or 8
        bits = bits or (lightmapKind(format).ordinal shl 4)
        return bits
    }

    private enum class LightmapKind { NONE, NORMALISED, SIGNED_SHORT }

    private fun lightmapKind(format: TranslatedVertexFormat): LightmapKind {
        if (!format.hasLightmap) {
            return LightmapKind.NONE
        }
        val attribute = format.format.attributes.firstOrNull { it.location == VertexLocations.UV1 }
            ?: return LightmapKind.NONE
        return when (attribute.format) {
            VertexAttributeFormat.SHORT2, VertexAttributeFormat.SHORT4 -> LightmapKind.SIGNED_SHORT
            else -> LightmapKind.NORMALISED
        }
    }
}

package re.lilith.kalia.shader

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.shader.*
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations

object CoreShaders {
    private const val TEXGEN_BIT = 1 shl 8
    private const val INSTANCED_BIT = 1 shl 9
    private const val TEXTURE_ARRAY_BIT = 1 shl 10
    private const val TEXTURE_SLOTS_BIT = 1 shl 11

    private val lock = Any()
    private val programs = Int2ObjectOpenHashMap<ShaderProgram>()

    private val gameState = CoreShadersData()
    private val renderState = CoreShadersData()

    private val state: CoreShadersData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    fun programFor(format: TranslatedVertexFormat, texGen: Boolean = false): ShaderProgram {
        val active = state
        val memo = active.lastProgram
        if (memo != null && active.lastFormat === format && active.lastTexGen == texGen) {
            return memo
        }
        val signature = signature(format) or (if (texGen) TEXGEN_BIT else 0)
        return synchronized(lock) {
            programs.getOrPut(signature) {
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
        }.also {
            active.lastFormat = format
            active.lastTexGen = texGen
            active.lastProgram = it
        }
    }

    /**
     * A core program that samples one of [ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT] bound textures per
     * vertex, so draws differing only in texture can be merged into one.
     */
    fun slottedProgramFor(format: TranslatedVertexFormat): ShaderProgram {
        val signature = signature(format) or TEXTURE_SLOTS_BIT
        return synchronized(lock) {
            programs.getOrPut(signature) {
                build(
                    label = "kalia/core/${format.shaderKey}-slots",
                    key = "${format.shaderKey}-slots",
                    file = "core",
                    format = format,
                    texGen = false,
                    signature = signature,
                    textureSlots = true,
                )
            }
        }
    }

    fun instancedProgramFor(format: TranslatedVertexFormat, textureArray: Boolean = false): ShaderProgram {
        val signature = signature(format) or INSTANCED_BIT or (if (textureArray) TEXTURE_ARRAY_BIT else 0)
        val suffix = if (textureArray) "-instanced-array" else "-instanced"
        return synchronized(lock) {
            programs.getOrPut(signature) {
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
    }

    private fun build(
        label: String,
        key: String,
        file: String,
        format: TranslatedVertexFormat,
        texGen: Boolean,
        signature: Int,
        textureArray: Boolean = false,
        textureSlots: Boolean = false,
    ): ShaderProgram {
        val defines = buildList {
            if (format.hasColor) add("HAS_COLOR")
            if (format.hasTexture) add("HAS_TEXTURE")
            if (format.hasLightmap) add("HAS_LIGHTMAP")
            if (lightmapKind(format) == LightmapKind.SIGNED_SHORT) add("LIGHTMAP_SIGNED_SHORT")
            if (format.hasNormal) add("HAS_NORMAL")
            if (texGen) add("TEXGEN")
            if (textureArray) add("TEXTURE_ARRAY")
            if (textureSlots) add("TEXTURE_SLOTS")
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
                if (textureSlots) {
                    for (slot in 1 until ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT) {
                        add(
                            ShaderBinding(
                                name = "kaliaSlot$slot",
                                binding = ShaderPrelude.Bindings.TEXTURE_SLOT_BASE + slot - 1,
                                kind = BindingKind.TEXTURE,
                                stages = setOf(ShaderStage.FRAGMENT),
                            ),
                        )
                    }
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
                        kind = BindingKind.UNIFORM_BUFFER_DYNAMIC,
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

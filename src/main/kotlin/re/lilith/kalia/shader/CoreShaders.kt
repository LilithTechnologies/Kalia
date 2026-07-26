package re.lilith.kalia.shader

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.shader.*
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations

// janky state based shader gen
// works, but is janky
object CoreShaders {
    private const val TEXGEN_BIT = 1 shl 8

    private val programs = HashMap<Int, ShaderProgram>()

    fun programFor(format: TranslatedVertexFormat, texGen: Boolean = false): ShaderProgram {
        val signature = signature(format) or (if (texGen) TEXGEN_BIT else 0)
        return programs.getOrPut(signature) { generate(format, texGen) }
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

    private fun generate(format: TranslatedVertexFormat, texGen: Boolean): ShaderProgram = ShaderProgram(
        label = "kalia/core/${shaderKey(format, texGen)}",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("${shaderKey(format, texGen)}.vert", vertexSource(format, texGen)),
            ShaderStage.FRAGMENT to ShaderSource.Glsl(
                "${shaderKey(format, texGen)}.frag",
                fragmentSource(format, texGen)
            ),
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
    )

    private fun shaderKey(format: TranslatedVertexFormat, texGen: Boolean): String =
        if (texGen) "${format.shaderKey}-texgen" else format.shaderKey

    private fun vertexSource(format: TranslatedVertexFormat, texGen: Boolean): String = buildString {
        appendLine("#version 450")
        appendLine("layout(location = ${VertexLocations.POSITION}) in vec3 inPosition;")
        if (format.hasColor) {
            appendLine("layout(location = ${VertexLocations.COLOR}) in vec4 inColor;")
        }
        if (format.hasTexture) {
            appendLine("layout(location = ${VertexLocations.UV0}) in vec2 inUv0;")
        }
        if (format.hasLightmap) {
            appendLine("layout(location = ${VertexLocations.UV1}) in ${lightmapInputType(format)} inUv1;")
        }
        if (format.hasNormal) {
            appendLine("layout(location = ${VertexLocations.NORMAL}) in vec4 inNormal;")
        }

        appendLine("layout(location = 0) out vec4 vColor;")
        appendLine("layout(location = 1) out ${if (texGen) "vec4" else "vec2"} vUv0;")
        appendLine("layout(location = 2) out vec2 vUv1;")
        appendLine("layout(location = 3) out vec3 vNormal;")
        appendLine("layout(location = 4) out float vViewDistance;")
        append(ShaderPrelude.PUSH_BLOCK)
        append(ShaderPrelude.SCENE_BLOCK)

        appendLine("void main() {")
        appendLine("    vec3 position = inPosition + kaliaModelOffset.xyz;")
        appendLine("    vec4 eye = kaliaModelView * vec4(position, 1.0);")
        appendLine("    vViewDistance = length(eye.xyz);")
        appendLine("    gl_Position = kaliaProjection * eye;")
        appendLine(
            if (format.hasColor) {
                "    vColor = inColor * kaliaShaderColor;"
            } else {
                "    vColor = kaliaShaderColor;"
            },
        )
        if (texGen) {
            // texgen impl
            appendLine("    vec4 genObject = vec4(position, 1.0);")
            appendLine("    vec4 gen = vec4(")
            appendLine("        dot(kaliaTexGenPlane[0], mix(genObject, eye, kaliaTexGenSource.x)),")
            appendLine("        dot(kaliaTexGenPlane[1], mix(genObject, eye, kaliaTexGenSource.y)),")
            appendLine("        dot(kaliaTexGenPlane[2], mix(genObject, eye, kaliaTexGenSource.z)),")
            appendLine("        dot(kaliaTexGenPlane[3], mix(genObject, eye, kaliaTexGenSource.w)));")
            appendLine("    vUv0 = kaliaTextureMatrix * gen;")
        } else {
            appendLine(
                if (format.hasTexture) {
                    "    vUv0 = (kaliaTextureMatrix * vec4(inUv0, 0.0, 1.0)).xy;"
                } else {
                    "    vUv0 = vec2(0.0);"
                },
            )
        }
        appendLine("    vUv1 = ${lightmapExpression(format)};")
        appendLine(
            if (format.hasNormal) {
                "    vNormal = mat3(kaliaModelView) * inNormal.xyz;"
            } else {
                "    vNormal = vec3(0.0, 1.0, 0.0);"
            },
        )
        appendLine("}")
    }

    private fun fragmentSource(format: TranslatedVertexFormat, texGen: Boolean): String = buildString {
        appendLine("#version 450")
        appendLine("layout(location = 0) in vec4 vColor;")
        appendLine("layout(location = 1) in ${if (texGen) "vec4" else "vec2"} vUv0;")
        appendLine("layout(location = 2) in vec2 vUv1;")
        appendLine("layout(location = 3) in vec3 vNormal;")
        appendLine("layout(location = 4) in float vViewDistance;")
        appendLine("layout(location = 0) out vec4 fragColor;")
        if (format.hasTexture || texGen) {
            appendLine(
                "layout(binding = ${ShaderPrelude.Bindings.BASE_TEXTURE}) uniform sampler2D kaliaBaseTexture;",
            )
        }
        appendLine(
            "layout(binding = ${ShaderPrelude.Bindings.LIGHTMAP_TEXTURE}) uniform sampler2D kaliaLightmapTexture;",
        )
        append(ShaderPrelude.PUSH_BLOCK)
        append(ShaderPrelude.SCENE_BLOCK)
        append(ShaderPrelude.FOG_FUNCTION)
        append(ShaderPrelude.LIGHTING_FUNCTION)
        append(ShaderPrelude.OVERLAY_FUNCTION)

        appendLine("void main() {")
        appendLine("    vec4 color = vColor;")
        if (texGen) {
            appendLine("    color *= textureProj(kaliaBaseTexture, vUv0);")
        } else if (format.hasTexture) {
            appendLine("    color *= texture(kaliaBaseTexture, vUv0);")
        }
        appendLine("    color.rgb = kaliaApplyOverlay(color.rgb);")
        if (format.hasNormal) {
            appendLine("    color.rgb = kaliaApplyDiffuse(color.rgb, vNormal);")
        }
        if (format.hasLightmap) {
            appendLine("    color.rgb *= texture(kaliaLightmapTexture, vUv1).rgb;")
        } else if (format.hasTexture) {
            appendLine("    if (KALIA_LIGHTMAP_ENABLED) {")
            appendLine("        color.rgb *= texture(kaliaLightmapTexture, KALIA_LIGHTMAP_COORDS).rgb;")
            appendLine("    }")
        }
        appendLine("    if (color.a <= KALIA_ALPHA_CUTOUT) {")
        appendLine("        discard;")
        appendLine("    }")
        appendLine("    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);")
        appendLine("    fragColor = color;")
        appendLine("}")
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

    private fun lightmapInputType(format: TranslatedVertexFormat): String =
        if (lightmapKind(format) == LightmapKind.SIGNED_SHORT) "ivec2" else "vec2"

    private fun lightmapExpression(format: TranslatedVertexFormat): String = when (lightmapKind(format)) {
        LightmapKind.NONE -> "KALIA_LIGHTMAP_COORDS"
        LightmapKind.NORMALISED -> "inUv1"
        LightmapKind.SIGNED_SHORT -> "vec2(inUv1) / 256.0"
    }
}

package re.lilith.kalia.vertex

import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormatElement
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import java.util.*
import re.lilith.kalia.renderer.format.VertexFormat as KaliaVertexFormat


object VertexFormatBridge {
    private val cache = IdentityHashMap<VertexFormat, TranslatedVertexFormat>()

    fun translate(format: VertexFormat): TranslatedVertexFormat =
        cache.getOrPut(format) { build(format) }

    private fun build(format: VertexFormat): TranslatedVertexFormat {
        var hasColor = false
        var hasTexture = false
        var hasLightmap = false
        var hasNormal = false
        var uvIndex = 0

        val builder = KaliaVertexFormat.Builder(VertexStepMode.VERTEX)
        val elements = format.elements
        for (index in elements.indices) {
            val element = elements[index]
            val usage = element.type
            if (usage == VertexFormatElement.Type.PADDING) {
                continue
            }

            val offset = format.getIndex(index)
            when (usage) {
                VertexFormatElement.Type.POSITION ->
                    builder.attributeAt("position", VertexLocations.POSITION, attributeFormat(element), offset)

                VertexFormatElement.Type.COLOR -> {
                    hasColor = true
                    builder.attributeAt("color", VertexLocations.COLOR, attributeFormat(element), offset)
                }

                VertexFormatElement.Type.UV -> {
                    if (uvIndex == 0) {
                        hasTexture = true
                        builder.attributeAt("uv0", VertexLocations.UV0, attributeFormat(element), offset)
                    } else if (uvIndex == 1) {
                        hasLightmap = true
                        builder.attributeAt("uv1", VertexLocations.UV1, attributeFormat(element), offset)
                    }
                    uvIndex++
                }

                VertexFormatElement.Type.NORMAL -> {
                    hasNormal = true
                    builder.attributeAt("normal", VertexLocations.NORMAL, attributeFormat(element), offset)
                }

                else -> Unit
            }
        }

        return TranslatedVertexFormat(
            format = builder.build(stride = format.vertexSize),
            shaderKey = shaderKey(hasColor, hasTexture, hasLightmap, hasNormal),
            hasColor = hasColor,
            hasTexture = hasTexture,
            hasLightmap = hasLightmap,
            hasNormal = hasNormal,
        )
    }

    private fun attributeFormat(element: VertexFormatElement): VertexAttributeFormat {
        val count = element.count
        return when (element.format) {
            VertexFormatElement.Format.FLOAT -> when (count) {
                1 -> VertexAttributeFormat.FLOAT
                2 -> VertexAttributeFormat.FLOAT2
                3 -> VertexAttributeFormat.FLOAT3
                else -> VertexAttributeFormat.FLOAT4
            }

            VertexFormatElement.Format.UNSIGNED_BYTE -> VertexAttributeFormat.UNORM8X4
            VertexFormatElement.Format.BYTE -> VertexAttributeFormat.SNORM8X4
            VertexFormatElement.Format.UNSIGNED_SHORT -> VertexAttributeFormat.UNORM16X2
            VertexFormatElement.Format.SHORT -> if (count > 2) {
                VertexAttributeFormat.SHORT4
            } else {
                VertexAttributeFormat.SHORT2
            }

            VertexFormatElement.Format.UNSIGNED_INT, VertexFormatElement.Format.INT ->
                if (count > 1) VertexAttributeFormat.UINT2 else VertexAttributeFormat.UINT

            else -> VertexAttributeFormat.FLOAT4
        }
    }

    private fun shaderKey(
        color: Boolean,
        texture: Boolean,
        lightmap: Boolean,
        normal: Boolean,
    ): String = buildString {
        append("position")
        if (color) append("_color")
        if (texture) append("_texture")
        if (lightmap) append("_light")
        if (normal) append("_normal")
    }
}

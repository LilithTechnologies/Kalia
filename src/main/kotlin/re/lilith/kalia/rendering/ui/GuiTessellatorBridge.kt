package re.lilith.kalia.rendering.ui

import org.joml.Vector4f
import org.lwjgl.opengl.GL11.GL_QUADS
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.rendering.ui.item.GuiBuiltinItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations
import java.nio.ByteBuffer

object GuiTessellatorBridge {
    private const val VERTICES_PER_QUAD = 4

    private val position = Vector4f()

    private val cornerX = FloatArray(VERTICES_PER_QUAD)
    private val cornerY = FloatArray(VERTICES_PER_QUAD)
    private val cornerU = FloatArray(VERTICES_PER_QUAD)
    private val cornerV = FloatArray(VERTICES_PER_QUAD)
    private val cornerColour = IntArray(VERTICES_PER_QUAD)

    fun tryCapture(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
    ): Boolean {
        if (!UI.isRecording) {
            return false
        }
        if (GuiBuiltinItems.isReplaying || GuiEntityPreview.isReplaying) {
            return false
        }
        if (format.hasNormal) {
            return false
        }
        if (glMode != GL_QUADS || vertexCount < VERTICES_PER_QUAD || vertexCount % VERTICES_PER_QUAD != 0) {
            return false
        }

        val layout = format.format
        val positionAttribute = layout.attributes.firstOrNull { it.location == VertexLocations.POSITION }
            ?: return false
        if (positionAttribute.format != VertexAttributeFormat.FLOAT3) {
            return false
        }

        val colourAttribute = layout.attributes.firstOrNull { it.location == VertexLocations.COLOR }
        if (colourAttribute != null && colourAttribute.format != VertexAttributeFormat.UNORM8X4) {
            return false
        }
        val uvAttribute = layout.attributes.firstOrNull { it.location == VertexLocations.UV0 }
        if (uvAttribute != null && uvAttribute.format != VertexAttributeFormat.FLOAT2) {
            return false
        }

        val textureId = if (uvAttribute != null && format.hasTexture) {
            UI.boundTextureId()
        } else {
            GuiTextureRegistry.UNTEXTURED
        }

        val base = MemoryAccess.addressOf(source) + source.position()
        val stride = layout.stride.toLong()
        val modelView = MatrixState.modelView()

        for (quad in 0 until vertexCount / VERTICES_PER_QUAD) {
            for (corner in 0 until VERTICES_PER_QUAD) {
                val vertex = base + (quad * VERTICES_PER_QUAD + corner) * stride

                position.set(
                    MemoryAccess.getFloat(vertex + positionAttribute.offset),
                    MemoryAccess.getFloat(vertex + positionAttribute.offset + 4),
                    MemoryAccess.getFloat(vertex + positionAttribute.offset + 8),
                    1f,
                )
                modelView.transform(position)

                cornerX[corner] = position.x
                cornerY[corner] = position.y
                cornerU[corner] = if (uvAttribute != null) MemoryAccess.getFloat(vertex + uvAttribute.offset) else 0f
                cornerV[corner] =
                    if (uvAttribute != null) MemoryAccess.getFloat(vertex + uvAttribute.offset + 4) else 0f
                cornerColour[corner] = if (colourAttribute != null) {
                    argbOf(MemoryAccess.getInt(vertex + colourAttribute.offset))
                } else {
                    UI.OPAQUE_WHITE
                }
            }

            submit(textureId)
        }
        return true
    }

    /**
     * Emits one captured quad.
     */
    private fun submit(textureId: Int) {
        var minU = cornerU[0]
        var maxU = cornerU[0]
        var minV = cornerV[0]
        var maxV = cornerV[0]
        for (corner in 1 until VERTICES_PER_QUAD) {
            minU = minOf(minU, cornerU[corner])
            maxU = maxOf(maxU, cornerU[corner])
            minV = minOf(minV, cornerV[corner])
            maxV = maxOf(maxV, cornerV[corner])
        }

        UI.state.submitCorners(
            layer = UI.layer,
            phase = UI.phase,
            textureId = textureId,
            scissorId = UI.scissors.current,
            material = UI.material,
            c0x = cornerX[3], c0y = cornerY[3],
            c1x = cornerX[0], c1y = cornerY[0],
            c2x = cornerX[1], c2y = cornerY[1],
            c3x = cornerX[2], c3y = cornerY[2],
            u0 = minU, v0 = minV, u1 = maxU, v1 = maxV,
            tintTop = cornerColour[3],
            tintBottom = cornerColour[0],
        )
    }

    private fun argbOf(packed: Int): Int {
        val red = packed and 0xFF
        val green = (packed ushr 8) and 0xFF
        val blue = (packed ushr 16) and 0xFF
        val alpha = (packed ushr 24) and 0xFF
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}

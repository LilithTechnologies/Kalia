package re.lilith.kalia.renderer.opengl.utils

import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL12C
import org.lwjgl.opengl.GL14C
import org.lwjgl.opengl.GL30C
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.FilterMode
import re.lilith.kalia.renderer.resource.WrapMode

internal object GlConvert {
    fun internalFormat(format: TextureFormat): Int = when (format) {
        TextureFormat.R8 -> GL30C.GL_R8
        TextureFormat.RG8 -> GL30C.GL_RG8
        TextureFormat.RGBA8 -> GL11C.GL_RGBA8
        // GL has no BGRA internal format
        TextureFormat.BGRA8 -> GL11C.GL_RGBA8
        TextureFormat.RGBA16F -> GL30C.GL_RGBA16F
        TextureFormat.RGBA32F -> GL30C.GL_RGBA32F
        TextureFormat.DEPTH32F -> GL30C.GL_DEPTH_COMPONENT32F
        TextureFormat.DEPTH24_STENCIL8 -> GL30C.GL_DEPTH24_STENCIL8
        TextureFormat.DEPTH32F_STENCIL8 -> GL30C.GL_DEPTH32F_STENCIL8
    }

    fun pixelFormat(format: TextureFormat): Int = when (format) {
        TextureFormat.R8 -> GL11C.GL_RED
        TextureFormat.RG8 -> GL30C.GL_RG
        TextureFormat.RGBA8 -> GL11C.GL_RGBA
        TextureFormat.BGRA8 -> GL12C.GL_BGRA
        TextureFormat.RGBA16F -> GL11C.GL_RGBA
        TextureFormat.RGBA32F -> GL11C.GL_RGBA
        TextureFormat.DEPTH32F -> GL11C.GL_DEPTH_COMPONENT
        TextureFormat.DEPTH24_STENCIL8 -> GL30C.GL_DEPTH_STENCIL
        TextureFormat.DEPTH32F_STENCIL8 -> GL30C.GL_DEPTH_STENCIL
    }

    fun pixelType(format: TextureFormat): Int = when (format) {
        TextureFormat.R8,
        TextureFormat.RG8,
        TextureFormat.RGBA8,
        TextureFormat.BGRA8,
            -> GL11C.GL_UNSIGNED_BYTE

        TextureFormat.RGBA16F -> GL30C.GL_HALF_FLOAT
        TextureFormat.RGBA32F -> GL11C.GL_FLOAT
        TextureFormat.DEPTH32F -> GL11C.GL_FLOAT
        TextureFormat.DEPTH24_STENCIL8 -> GL30C.GL_UNSIGNED_INT_24_8
        TextureFormat.DEPTH32F_STENCIL8 -> GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
    }

    fun minFilter(min: FilterMode, mip: FilterMode): Int = when (min) {
        FilterMode.NEAREST -> when (mip) {
            FilterMode.NEAREST -> GL11C.GL_NEAREST_MIPMAP_NEAREST
            FilterMode.LINEAR -> GL11C.GL_NEAREST_MIPMAP_LINEAR
        }

        FilterMode.LINEAR -> when (mip) {
            FilterMode.NEAREST -> GL11C.GL_LINEAR_MIPMAP_NEAREST
            FilterMode.LINEAR -> GL11C.GL_LINEAR_MIPMAP_LINEAR
        }
    }

    fun magFilter(mode: FilterMode): Int = when (mode) {
        FilterMode.NEAREST -> GL11C.GL_NEAREST
        FilterMode.LINEAR -> GL11C.GL_LINEAR
    }

    fun wrap(mode: WrapMode): Int = when (mode) {
        WrapMode.REPEAT -> GL11C.GL_REPEAT
        WrapMode.MIRROR -> GL14C.GL_MIRRORED_REPEAT
        WrapMode.CLAMP_TO_EDGE -> GL12C.GL_CLAMP_TO_EDGE
    }

    fun topology(topology: PrimitiveTopology): Int = when (topology) {
        PrimitiveTopology.POINTS -> GL11C.GL_POINTS
        PrimitiveTopology.LINES -> GL11C.GL_LINES
        PrimitiveTopology.LINE_STRIP -> GL11C.GL_LINE_STRIP
        PrimitiveTopology.TRIANGLES -> GL11C.GL_TRIANGLES
        PrimitiveTopology.TRIANGLE_STRIP -> GL11C.GL_TRIANGLE_STRIP
    }

    fun polygonMode(mode: PolygonMode): Int = when (mode) {
        PolygonMode.FILL -> GL11C.GL_FILL
        PolygonMode.LINE -> GL11C.GL_LINE
        PolygonMode.POINT -> GL11C.GL_POINT
    }

    fun compare(function: CompareFunction): Int = when (function) {
        CompareFunction.NEVER -> GL11C.GL_NEVER
        CompareFunction.LESS -> GL11C.GL_LESS
        CompareFunction.EQUAL -> GL11C.GL_EQUAL
        CompareFunction.LESS_EQUAL -> GL11C.GL_LEQUAL
        CompareFunction.GREATER -> GL11C.GL_GREATER
        CompareFunction.NOT_EQUAL -> GL11C.GL_NOTEQUAL
        CompareFunction.GREATER_EQUAL -> GL11C.GL_GEQUAL
        CompareFunction.ALWAYS -> GL11C.GL_ALWAYS
    }

    fun blendFactor(factor: BlendFactor): Int = when (factor) {
        BlendFactor.ZERO -> GL11C.GL_ZERO
        BlendFactor.ONE -> GL11C.GL_ONE
        BlendFactor.SRC_COLOR -> GL11C.GL_SRC_COLOR
        BlendFactor.ONE_MINUS_SRC_COLOR -> GL11C.GL_ONE_MINUS_SRC_COLOR
        BlendFactor.DST_COLOR -> GL11C.GL_DST_COLOR
        BlendFactor.ONE_MINUS_DST_COLOR -> GL11C.GL_ONE_MINUS_DST_COLOR
        BlendFactor.SRC_ALPHA -> GL11C.GL_SRC_ALPHA
        BlendFactor.ONE_MINUS_SRC_ALPHA -> GL11C.GL_ONE_MINUS_SRC_ALPHA
        BlendFactor.DST_ALPHA -> GL11C.GL_DST_ALPHA
        BlendFactor.ONE_MINUS_DST_ALPHA -> GL11C.GL_ONE_MINUS_DST_ALPHA
        BlendFactor.SRC_ALPHA_SATURATE -> GL11C.GL_SRC_ALPHA_SATURATE
        BlendFactor.CONSTANT_COLOR -> GL14C.GL_CONSTANT_COLOR
        BlendFactor.ONE_MINUS_CONSTANT_COLOR -> GL14C.GL_ONE_MINUS_CONSTANT_COLOR
    }

    fun blendOp(op: BlendOp): Int = when (op) {
        BlendOp.ADD -> GL14C.GL_FUNC_ADD
        BlendOp.SUBTRACT -> GL14C.GL_FUNC_SUBTRACT
        BlendOp.REVERSE_SUBTRACT -> GL14C.GL_FUNC_REVERSE_SUBTRACT
        BlendOp.MIN -> GL14C.GL_MIN
        BlendOp.MAX -> GL14C.GL_MAX
    }

    fun logicOp(op: LogicOp): Int = when (op) {
        LogicOp.CLEAR -> GL11C.GL_CLEAR
        LogicOp.AND -> GL11C.GL_AND
        LogicOp.AND_REVERSE -> GL11C.GL_AND_REVERSE
        LogicOp.COPY -> GL11C.GL_COPY
        LogicOp.AND_INVERTED -> GL11C.GL_AND_INVERTED
        LogicOp.NO_OP -> GL11C.GL_NOOP
        LogicOp.XOR -> GL11C.GL_XOR
        LogicOp.OR -> GL11C.GL_OR
        LogicOp.NOR -> GL11C.GL_NOR
        LogicOp.EQUIVALENT -> GL11C.GL_EQUIV
        LogicOp.INVERT -> GL11C.GL_INVERT
        LogicOp.OR_REVERSE -> GL11C.GL_OR_REVERSE
        LogicOp.COPY_INVERTED -> GL11C.GL_COPY_INVERTED
        LogicOp.OR_INVERTED -> GL11C.GL_OR_INVERTED
        LogicOp.NAND -> GL11C.GL_NAND
        LogicOp.SET -> GL11C.GL_SET
    }

    fun indexType(format: IndexFormat): Int = when (format) {
        IndexFormat.UINT16 -> GL11C.GL_UNSIGNED_SHORT
        IndexFormat.UINT32 -> GL11C.GL_UNSIGNED_INT
    }

    /**
     * How one vertex attribute is fed to `glVertexAttribPointer` or the I variant
     */
    data class VertexAttribPointer(
        val componentCount: Int,
        val componentType: Int,
        val normalized: Boolean,
        val integer: Boolean,
    )

    fun vertexAttribute(format: VertexAttributeFormat): VertexAttribPointer = when (format) {
        VertexAttributeFormat.FLOAT -> VertexAttribPointer(1, GL11C.GL_FLOAT, normalized = false, integer = false)
        VertexAttributeFormat.FLOAT2 -> VertexAttribPointer(2, GL11C.GL_FLOAT, normalized = false, integer = false)
        VertexAttributeFormat.FLOAT3 -> VertexAttribPointer(3, GL11C.GL_FLOAT, normalized = false, integer = false)
        VertexAttributeFormat.FLOAT4 -> VertexAttribPointer(4, GL11C.GL_FLOAT, normalized = false, integer = false)
        VertexAttributeFormat.UNORM8X4 -> VertexAttribPointer(4, GL11C.GL_UNSIGNED_BYTE, true, integer = false)
        VertexAttributeFormat.SNORM8X4 -> VertexAttribPointer(4, GL11C.GL_BYTE, normalized = true, integer = false)
        VertexAttributeFormat.UNORM16X2 -> VertexAttribPointer(
            2, GL11C.GL_UNSIGNED_SHORT,
            normalized = true,
            integer = false
        )

        VertexAttributeFormat.SHORT2 -> VertexAttribPointer(2, GL11C.GL_SHORT, normalized = false, integer = true)
        VertexAttributeFormat.SHORT4 -> VertexAttribPointer(4, GL11C.GL_SHORT, normalized = false, integer = true)
        VertexAttributeFormat.UINT -> VertexAttribPointer(1, GL11C.GL_UNSIGNED_INT, normalized = false, integer = true)
        VertexAttributeFormat.UINT2 -> VertexAttribPointer(2, GL11C.GL_UNSIGNED_INT, normalized = false, integer = true)
        VertexAttributeFormat.UINT8X4 -> VertexAttribPointer(
            4, GL11C.GL_UNSIGNED_BYTE,
            normalized = false,
            integer = true
        )

        VertexAttributeFormat.UINT16X2 -> VertexAttribPointer(
            2, GL11C.GL_UNSIGNED_SHORT,
            normalized = false,
            integer = true
        )
    }
}

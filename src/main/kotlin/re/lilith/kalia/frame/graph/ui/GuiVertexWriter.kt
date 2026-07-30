package re.lilith.kalia.frame.graph.ui

import org.joml.Matrix4f
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations
import java.util.IdentityHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rewrites immediate-mode vertices into [GuiBatcher.FORMAT]
 */
internal object GuiVertexWriter {
    const val VERTEX_BYTES = 32

    private const val POSITION_OFFSET = 0
    private const val COLOR_OFFSET = 12
    private const val UV_OFFSET = 16
    private const val NORMAL_OFFSET = 24
    private const val SLOT_OFFSET = 28

    private const val DEFAULT_NORMAL = 127 shl 8

    class Layout(val stride: Int, val position: Int, val color: Int, val uv: Int, val normal: Int)

    private val layouts = IdentityHashMap<TranslatedVertexFormat, Any>()
    private val UNSUPPORTED = Any()

    private var m00 = 1f
    private var m01 = 0f
    private var m02 = 0f
    private var m10 = 0f
    private var m11 = 1f
    private var m12 = 0f
    private var m20 = 0f
    private var m21 = 0f
    private var m22 = 1f
    private var m30 = 0f
    private var m31 = 0f
    private var m32 = 0f

    private var offsetX = 0f
    private var offsetY = 0f
    private var offsetZ = 0f

    private var red = 1f
    private var green = 1f
    private var blue = 1f
    private var alpha = 1f

    private var axisAligned = true

    private var tinting = false

    private var textureSlot = 0

    fun setTextureSlot(slot: Int) {
        textureSlot = slot
    }

    fun layoutFor(format: TranslatedVertexFormat): Layout? {
        val cached = layouts.getOrPut(format) { resolve(format) ?: UNSUPPORTED }
        return cached as? Layout
    }

    private fun resolve(format: TranslatedVertexFormat): Layout? {
        var position = -1
        var color = -1
        var uv = -1
        var normal = -1
        for (attribute in format.format.attributes) {
            val offset = attribute.offset
            when (attribute.location) {
                VertexLocations.POSITION ->
                    if (attribute.format == VertexAttributeFormat.FLOAT3) position = offset else return null

                VertexLocations.COLOR ->
                    if (attribute.format == VertexAttributeFormat.UNORM8X4) color = offset else return null

                VertexLocations.UV0 ->
                    if (attribute.format == VertexAttributeFormat.FLOAT2) uv = offset else return null

                VertexLocations.NORMAL ->
                    if (attribute.format == VertexAttributeFormat.SNORM8X4) normal = offset else return null

                else -> return null
            }
        }
        if (position < 0) {
            return null
        }
        return Layout(format.format.stride, position, color, uv, normal)
    }

    fun setTransform(matrix: Matrix4f, offsetX: Float, offsetY: Float, offsetZ: Float) {
        m00 = matrix.m00()
        m01 = matrix.m01()
        m02 = matrix.m02()
        m10 = matrix.m10()
        m11 = matrix.m11()
        m12 = matrix.m12()
        m20 = matrix.m20()
        m21 = matrix.m21()
        m22 = matrix.m22()
        m30 = matrix.m30()
        m31 = matrix.m31()
        m32 = matrix.m32()

        this.offsetX = offsetX
        this.offsetY = offsetY
        this.offsetZ = offsetZ
        axisAligned = m01 == 0f && m02 == 0f && m10 == 0f && m12 == 0f && m20 == 0f && m21 == 0f
    }

    fun setColor(red: Float, green: Float, blue: Float, alpha: Float) {
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
        tinting = red != 1f || green != 1f || blue != 1f || alpha != 1f
    }

    fun write(source: Long, layout: Layout, vertexCount: Int, target: Long) {
        val aligned = axisAligned
        val tints = tinting
        val positionOffset = layout.position
        val colorOffset = layout.color
        val uvOffset = layout.uv
        val stride = layout.stride
        val slot = textureSlot

        var src = source
        var dst = target
        repeat(vertexCount) {
            val x = MemoryAccess.getFloat(src + positionOffset) + offsetX
            val y = MemoryAccess.getFloat(src + positionOffset + 4) + offsetY
            val z = MemoryAccess.getFloat(src + positionOffset + 8) + offsetZ

            if (aligned) {
                MemoryAccess.putFloat(dst + POSITION_OFFSET, m00 * x + m30)
                MemoryAccess.putFloat(dst + POSITION_OFFSET + 4, m11 * y + m31)
                MemoryAccess.putFloat(dst + POSITION_OFFSET + 8, m22 * z + m32)
            } else {
                MemoryAccess.putFloat(dst + POSITION_OFFSET, m00 * x + m10 * y + m20 * z + m30)
                MemoryAccess.putFloat(dst + POSITION_OFFSET + 4, m01 * x + m11 * y + m21 * z + m31)
                MemoryAccess.putFloat(dst + POSITION_OFFSET + 8, m02 * x + m12 * y + m22 * z + m32)
            }

            val packed = if (colorOffset >= 0) MemoryAccess.getInt(src + colorOffset) else -1
            MemoryAccess.putInt(dst + COLOR_OFFSET, if (tints) tint(packed) else packed)

            if (uvOffset >= 0) {
                MemoryAccess.putFloat(dst + UV_OFFSET, MemoryAccess.getFloat(src + uvOffset))
                MemoryAccess.putFloat(dst + UV_OFFSET + 4, MemoryAccess.getFloat(src + uvOffset + 4))
            } else {
                MemoryAccess.putFloat(dst + UV_OFFSET, 0f)
                MemoryAccess.putFloat(dst + UV_OFFSET + 4, 0f)
            }

            writeNormal(src, layout, dst + NORMAL_OFFSET, aligned)
            MemoryAccess.putInt(dst + SLOT_OFFSET, slot)

            src += stride
            dst += VERTEX_BYTES
        }
    }

    private fun tint(packed: Int): Int {
        val r = channel(packed and 0xFF, red)
        val g = channel((packed ushr 8) and 0xFF, green)
        val b = channel((packed ushr 16) and 0xFF, blue)
        val a = channel((packed ushr 24) and 0xFF, alpha)
        return r or (g shl 8) or (b shl 16) or (a shl 24)
    }

    private fun channel(value: Int, scale: Float): Int = (value * scale + 0.5f).toInt().coerceIn(0, 255)

    private fun writeNormal(src: Long, layout: Layout, dst: Long, aligned: Boolean) {
        if (layout.normal < 0) {
            MemoryAccess.putInt(dst, DEFAULT_NORMAL)
            return
        }

        val nx = MemoryAccess.getByte(src + layout.normal).toFloat()
        val ny = MemoryAccess.getByte(src + layout.normal + 1).toFloat()
        val nz = MemoryAccess.getByte(src + layout.normal + 2).toFloat()

        var tx: Float
        var ty: Float
        var tz: Float
        if (aligned) {
            tx = m00 * nx
            ty = m11 * ny
            tz = m22 * nz
        } else {
            tx = m00 * nx + m10 * ny + m20 * nz
            ty = m01 * nx + m11 * ny + m21 * nz
            tz = m02 * nx + m12 * ny + m22 * nz
        }

        val length = sqrt(tx * tx + ty * ty + tz * tz)
        if (length > 1e-6f) {
            val scale = 127f / length
            tx *= scale
            ty *= scale
            tz *= scale
        }

        MemoryAccess.putByte(dst, clampByte(tx))
        MemoryAccess.putByte(dst + 1, clampByte(ty))
        MemoryAccess.putByte(dst + 2, clampByte(tz))
        MemoryAccess.putByte(dst + 3, 0)
    }

    private fun clampByte(value: Float): Byte = value.roundToInt().coerceIn(-127, 127).toByte()
}

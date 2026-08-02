package re.lilith.kalia.rendering.ui.item

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

object GuiItemMeshBuilder {
    const val VERTEX_BYTES = 28
    const val INTS_PER_VERTEX = 7
    const val VERTICES_PER_QUAD = 4

    private const val COLOUR_INT_INDEX = 3
    private const val UV_INT_INDEX = 4
    private const val NORMAL_BYTE_OFFSET = 24

    fun appendQuad(
        target: ByteBuffer,
        vertexData: IntArray,
        argb: Int,
        normalX: Int,
        normalY: Int,
        normalZ: Int,
        brightness: Float = 1f,
        uv: UvTransform? = null,
    ) {
        require(vertexData.size >= INTS_PER_VERTEX * VERTICES_PER_QUAD) {
            "A baked quad needs ${INTS_PER_VERTEX * VERTICES_PER_QUAD} ints, got ${vertexData.size}."
        }

        val scale = brightness.coerceIn(0f, 1f)
        val red = (((argb ushr 16 and 0xFF) * scale).toInt().coerceIn(0, 255)).toByte()
        val green = (((argb ushr 8 and 0xFF) * scale).toInt().coerceIn(0, 255)).toByte()
        val blue = (((argb and 0xFF) * scale).toInt().coerceIn(0, 255)).toByte()
        val alpha = (argb ushr 24 and 0xFF).toByte()

        for (vertex in 0 until VERTICES_PER_QUAD) {
            val base = vertex * INTS_PER_VERTEX
            val start = target.position()

            for (word in 0 until INTS_PER_VERTEX) {
                target.putInt(vertexData[base + word])
            }

            target.put(start + COLOUR_INT_INDEX * Int.SIZE_BYTES, red)
            target.put(start + COLOUR_INT_INDEX * Int.SIZE_BYTES + 1, green)
            target.put(start + COLOUR_INT_INDEX * Int.SIZE_BYTES + 2, blue)
            target.put(start + COLOUR_INT_INDEX * Int.SIZE_BYTES + 3, alpha)

            target.put(start + NORMAL_BYTE_OFFSET, packNormal(normalX))
            target.put(start + NORMAL_BYTE_OFFSET + 1, packNormal(normalY))
            target.put(start + NORMAL_BYTE_OFFSET + 2, packNormal(normalZ))
            target.put(start + NORMAL_BYTE_OFFSET + 3, 0)

            if (uv != null) {
                val u = Float.fromBits(vertexData[base + UV_INT_INDEX])
                val v = Float.fromBits(vertexData[base + UV_INT_INDEX + 1])
                target.putFloat(start + UV_INT_INDEX * Int.SIZE_BYTES, uv.applyU(u, v))
                target.putFloat(start + UV_INT_INDEX * Int.SIZE_BYTES + 4, uv.applyV(u, v))
            }
        }
    }

    class UvTransform(
        private val m00: Float,
        private val m01: Float,
        private val m10: Float,
        private val m11: Float,
        private val offsetU: Float,
        private val offsetV: Float,
    ) {
        fun applyU(u: Float, v: Float): Float = m00 * u + m10 * v + offsetU

        fun applyV(u: Float, v: Float): Float = m01 * u + m11 * v + offsetV

        companion object {
            fun glint(scale: Float, degrees: Float, phase: Float): UvTransform {
                val radians = Math.toRadians(degrees.toDouble())
                val cos = cos(radians).toFloat() * scale
                val sin = sin(radians).toFloat() * scale
                return UvTransform(cos, sin, -sin, cos, phase, 0f)
            }
        }
    }

    fun allocate(quadCount: Int): ByteBuffer = ByteBuffer
        .allocateDirect(quadCount * VERTICES_PER_QUAD * VERTEX_BYTES)
        .order(ByteOrder.nativeOrder())

    private fun packNormal(component: Int) = (component * 127).coerceIn(-127, 127).toByte()
}

package re.lilith.kalia.draw

import net.minecraft.client.render.VertexFormats
import re.lilith.kalia.vertex.VertexFormatBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImmediateMode {
    private var mode = NOT_BEGUN
    private var vertexCount = 0
    private var u = 0f
    private var v = 0f
    private var vertices = allocate(INITIAL_VERTICES)

    private val format by lazy {
        VertexFormatBridge.translate(VertexFormats.POSITION_TEXTURE).also {
            require(it.format.stride == VERTEX_BYTES) {
                "POSITION_TEX has a stride of ${it.format.stride}, not $VERTEX_BYTES."
            }
        }
    }

    fun begin(glMode: Int) {
        mode = glMode
        vertexCount = 0
        u = 0f
        v = 0f
        vertices.clear()
    }

    fun texCoord(s: Float, t: Float) {
        u = s
        v = t
    }

    fun vertex(x: Float, y: Float, z: Float) {
        if (mode == NOT_BEGUN) {
            return
        }
        if (vertices.remaining() < VERTEX_BYTES) {
            grow()
        }
        vertices.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v)
        vertexCount++
    }

    fun end() {
        if (mode == NOT_BEGUN) {
            return
        }
        vertices.flip()
        KaliaDraw.drawTransient(vertices, format, mode, vertexCount)
        mode = NOT_BEGUN
    }

    private fun grow() {
        val larger = allocate(vertexCount * 2)
        vertices.flip()
        larger.put(vertices)
        vertices = larger
    }

    private fun allocate(vertexCapacity: Int): ByteBuffer =
        ByteBuffer.allocateDirect(vertexCapacity * VERTEX_BYTES).order(ByteOrder.nativeOrder())

    private const val VERTEX_BYTES = 20
    private const val INITIAL_VERTICES = 64
    private const val NOT_BEGUN = -1
}

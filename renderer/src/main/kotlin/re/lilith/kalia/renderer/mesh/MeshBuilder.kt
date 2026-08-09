package re.lilith.kalia.renderer.mesh

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a mesh on the CPU, then hands it to the GPU once
 */
class MeshBuilder(
    val vertexFormat: VertexFormat,
    initialVertexCapacity: Int = 256,
    builder: MeshBuilder.() -> Unit = {}
) {
    private var vertices: ByteBuffer = direct(initialVertexCapacity * vertexFormat.stride)
    private var indices: ByteBuffer = direct(initialVertexCapacity * 6)
    private var vertexStart = 0

    var vertexCount: Int = 0
        private set

    var indexCount: Int = 0
        private set

    val isEmpty: Boolean get() = indexCount == 0 && vertexCount == 0

    init {
        builder()
    }

    fun clear() {
        vertices.clear()
        indices.clear()
        vertexCount = 0
        indexCount = 0
        vertexStart = 0
    }

    fun float(value: Float): MeshBuilder = apply { reserveVertex(4).putFloat(value) }

    fun vec2(x: Float, y: Float): MeshBuilder = apply { reserveVertex(8).putFloat(x).putFloat(y) }

    fun vec3(x: Float, y: Float, z: Float): MeshBuilder =
        apply { reserveVertex(12).putFloat(x).putFloat(y).putFloat(z) }

    fun vec4(x: Float, y: Float, z: Float, w: Float): MeshBuilder =
        apply { reserveVertex(16).putFloat(x).putFloat(y).putFloat(z).putFloat(w) }

    fun addVertex(x: Float, y: Float, z: Float): MeshBuilder = apply { vec3(x, y, z); endVertex() }

    fun colorArgb(packed: Int): MeshBuilder = apply {
        reserveVertex(4)
            .put((packed ushr 16 and 0xFF).toByte())
            .put((packed ushr 8 and 0xFF).toByte())
            .put((packed and 0xFF).toByte())
            .put((packed ushr 24 and 0xFF).toByte())
    }

    fun bytes(a: Int, b: Int, c: Int, d: Int): MeshBuilder = apply {
        reserveVertex(4).put(a.toByte()).put(b.toByte()).put(c.toByte()).put(d.toByte())
    }

    fun shorts(a: Int, b: Int): MeshBuilder = apply {
        reserveVertex(4).putShort(a.toShort()).putShort(b.toShort())
    }

    fun uint(value: Int): MeshBuilder = apply { reserveVertex(4).putInt(value) }

    fun endVertex(): MeshBuilder = apply {
        val written = vertices.position() - vertexStart
        require(written == vertexFormat.stride) {
            "Vertex $vertexCount wrote $written bytes but the format stride is ${vertexFormat.stride}."
        }
        vertexStart = vertices.position()
        vertexCount++
    }

    fun index(value: Int): MeshBuilder = apply {
        reserveIndex().putInt(value)
        indexCount++
    }

    fun triangle(a: Int, b: Int, c: Int): MeshBuilder = apply { index(a); index(b); index(c) }

    fun quad(): MeshBuilder = apply {
        val base = vertexCount - 4
        require(base >= 0) { "quad() needs four vertices to have been written." }
        triangle(base, base + 1, base + 2)
        triangle(base, base + 2, base + 3)
    }

    /**
     * Uploads into freshly allocated stream buffers
     */
    fun upload(device: RenderDevice, label: String = "mesh"): UploadedMesh {
        require(vertices.position() == vertexStart) { "Unterminated vertex; call endVertex()." }
        check(vertexCount > 0) { "Cannot upload an empty mesh." }

        val vertexBuffer = device.createBuffer(
            BufferDescription(
                "$label-vertices",
                (vertexCount.toLong() * vertexFormat.stride),
                BufferUsage.STREAM,
                vertex = true
            ),
        )
        vertices.flip()
        vertexBuffer.write(vertices)
        vertices.limit(vertices.capacity()).position(vertexStart)

        val indexBuffer = if (indexCount == 0) {
            null
        } else {
            device.createBuffer(
                BufferDescription("$label-indices", indexCount.toLong() * 4, BufferUsage.STREAM, index = true),
            ).also { buffer ->
                indices.flip()
                buffer.write(indices)
                indices.limit(indices.capacity()).position(indexCount * 4)
            }
        }

        return UploadedMesh(vertexBuffer, indexBuffer, vertexCount, indexCount)
    }

    private fun reserveVertex(bytes: Int): ByteBuffer {
        if (vertices.remaining() < bytes) {
            vertices = grow(vertices, bytes)
        }
        return vertices
    }

    private fun reserveIndex(): ByteBuffer {
        if (indices.remaining() < 4) {
            indices = grow(indices, 4)
        }
        return indices
    }

    private fun grow(buffer: ByteBuffer, needed: Int): ByteBuffer {
        val target = ((buffer.capacity() + needed) * 2).coerceAtLeast(GROWTH_FLOOR)
        val grown = direct(target)
        val used = buffer.position()
        buffer.flip()
        grown.put(buffer)
        grown.position(used)
        return grown
    }

    private fun direct(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.coerceAtLeast(GROWTH_FLOOR)).order(ByteOrder.nativeOrder())

    private companion object {
        const val GROWTH_FLOOR = 4096
    }
}


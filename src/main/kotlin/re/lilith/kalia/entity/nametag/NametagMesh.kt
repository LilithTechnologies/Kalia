package re.lilith.kalia.entity.nametag

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

object NametagMesh {
    const val INDEX_COUNT: Int = 6

    val VERTEX_FORMAT = VertexFormat.of {
        attribute("position", 0, VertexAttributeFormat.FLOAT2)
    }

    private val VERTICES = floatArrayOf(
        0f, 0f,
        1f, 0f,
        1f, 1f,
        0f, 1f,
    )

    private val INDICES = intArrayOf(0, 1, 2, 2, 3, 0)

    private var vertexBuffer: GpuBuffer? = null
    private var indexBuffer: GpuBuffer? = null
    private var uploadedTo: RenderDevice? = null

    fun vertices(device: RenderDevice): GpuBuffer = ensureUploaded(device).first
    fun indices(device: RenderDevice): GpuBuffer = ensureUploaded(device).second

    private fun ensureUploaded(device: RenderDevice): Pair<GpuBuffer, GpuBuffer> {
        vertexBuffer?.let { v -> indexBuffer?.let { i -> if (uploadedTo === device) return v to i } }

        vertexBuffer?.close()
        indexBuffer?.close()

        val vertexBytes = ByteBuffer.allocateDirect(VERTICES.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        vertexBytes.asFloatBuffer().put(VERTICES)
        val vertices = device.createBuffer(
            BufferDescription(
                label = "kalia/nametag-mesh",
                sizeBytes = vertexBytes.capacity().toLong(),
                usage = BufferUsage.STATIC,
                vertex = true,
            ),
        ).also { it.write(vertexBytes) }

        val indexBytes = ByteBuffer.allocateDirect(INDICES.size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
        indexBytes.asIntBuffer().put(INDICES)
        val indices = device.createBuffer(
            BufferDescription(
                label = "kalia/nametag-indices",
                sizeBytes = indexBytes.capacity().toLong(),
                usage = BufferUsage.STATIC,
                index = true,
            ),
        ).also { it.write(indexBytes) }

        vertexBuffer = vertices
        indexBuffer = indices
        uploadedTo = device
        return vertices to indices
    }
}

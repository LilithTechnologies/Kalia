package re.lilith.kalia.renderer.mesh

import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.resource.GpuBuffer

class UploadedMesh(
    val vertexBuffer: GpuBuffer,
    val indexBuffer: GpuBuffer?,
    val vertexCount: Int,
    val indexCount: Int,
) : AutoCloseable {
    fun draw(encoder: PassEncoder, instanceCount: Int = 1) {
        encoder.bindVertexBuffer(0, vertexBuffer)
        if (indexBuffer != null) {
            encoder.bindIndexBuffer(indexBuffer, IndexFormat.UINT32)
            encoder.drawIndexed(indexCount, instanceCount)
        } else {
            encoder.draw(vertexCount, instanceCount)
        }
    }

    override fun close() {
        vertexBuffer.close()
        indexBuffer?.close()
    }
}

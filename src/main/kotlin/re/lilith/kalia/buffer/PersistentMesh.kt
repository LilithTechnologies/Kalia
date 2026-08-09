package re.lilith.kalia.buffer

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.vertex.TranslatedVertexFormat
import java.nio.ByteBuffer

class PersistentMesh(
    private val device: RenderDevice,
    private val label: String,
) : AutoCloseable {
    private var buffer: GpuBuffer? = null
    private var capacityBytes = 0L

    var vertexCount: Int = 0
        private set

    var format: TranslatedVertexFormat? = null
        private set

    val vertexBuffer: GpuBuffer?
        get() = if (vertexCount > 0) buffer else null

    val isEmpty: Boolean get() = vertexCount == 0

    fun upload(source: ByteBuffer?, format: TranslatedVertexFormat, vertexCount: Int) {
        if (source == null || vertexCount <= 0) {
            this.vertexCount = 0
            return
        }

        val byteCount = vertexCount.toLong() * format.format.stride
        require(byteCount <= source.remaining()) {
            "Mesh '$label' needs $byteCount bytes for $vertexCount vertices but only ${source.remaining()} were supplied."
        }

        ensureCapacity(byteCount)
        val view = source.slice()
        view.limit(byteCount.toInt())
        requireNotNull(buffer).write(view)

        this.vertexCount = vertexCount
        this.format = format
    }

    override fun close() {
        buffer?.close()
        buffer = null
        capacityBytes = 0L
        vertexCount = 0
        format = null
    }

    private fun ensureCapacity(byteCount: Long) {
        if (capacityBytes >= byteCount && buffer != null) {
            return
        }

        var target = capacityBytes.coerceAtLeast(MINIMUM_BYTES)
        while (target < byteCount) {
            target = target shl 1
        }

        buffer?.close()
        buffer = device.createBuffer(
            BufferDescription(
                label = label,
                sizeBytes = target,
                usage = BufferUsage.STATIC,
                vertex = true,
            ),
        )
        capacityBytes = target
    }

    private companion object {
        const val MINIMUM_BYTES = 4L * 1024L
    }
}

package re.lilith.kalia.buffer

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SharedIndexBuffer(private val device: RenderDevice) : AutoCloseable {
    private var quads: GpuBuffer? = null
    private var quadCapacity = 0

    private var fans: GpuBuffer? = null
    private var fanCapacity = 0

    fun forQuads(quadCount: Int): GpuBuffer {
        require(quadCount >= 0) { "quadCount must be >= 0." }
        quads?.takeIf { quadCount <= quadCapacity }?.let { return it }

        quadCapacity = growTo(quadCapacity, quadCount, INITIAL_QUADS)
        quads?.close()
        val capacity = quadCapacity
        return allocate("kalia/quad-indices", quadIndexCount(capacity)) { out ->
            for (quad in 0 until capacity) {
                val base = quad * VERTICES_PER_QUAD
                out.putInt(base).putInt(base + 1).putInt(base + 2)
                out.putInt(base).putInt(base + 2).putInt(base + 3)
            }
        }.also { quads = it }
    }

    fun quadIndexCount(quadCount: Int): Int = quadCount * INDICES_PER_QUAD

    fun forFan(vertexCount: Int): GpuBuffer {
        require(vertexCount >= 0) { "vertexCount must be >= 0." }
        fans?.takeIf { vertexCount <= fanCapacity }?.let { return it }

        fanCapacity = growTo(fanCapacity, vertexCount, INITIAL_FAN_VERTICES)
        fans?.close()
        val capacity = fanCapacity
        return allocate("kalia/fan-indices", fanIndexCount(capacity)) { out ->
            for (triangle in 0 until capacity - 2) {
                out.putInt(0).putInt(triangle + 1).putInt(triangle + 2)
            }
        }.also { fans = it }
    }

    fun fanIndexCount(vertexCount: Int): Int = (vertexCount - 2).coerceAtLeast(0) * INDICES_PER_TRIANGLE

    override fun close() {
        quads?.close()
        quads = null
        quadCapacity = 0
        fans?.close()
        fans = null
        fanCapacity = 0
    }

    private fun growTo(capacity: Int, required: Int, initial: Int): Int {
        var target = capacity.coerceAtLeast(initial)
        while (target < required) {
            target = target shl 1
        }
        return target
    }

    private fun allocate(label: String, indexCount: Int, fill: (ByteBuffer) -> Unit): GpuBuffer {
        val bytes = ByteBuffer
            .allocateDirect(indexCount * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        fill(bytes)
        bytes.flip()

        return device.createBuffer(
            BufferDescription(
                label = label,
                sizeBytes = bytes.remaining().toLong(),
                usage = BufferUsage.STATIC,
                index = true,
            ),
        ).also { it.write(bytes) }
    }

    private companion object {
        const val VERTICES_PER_QUAD = 4
        const val INDICES_PER_QUAD = 6
        const val INDICES_PER_TRIANGLE = 3

        const val INITIAL_QUADS = 4096

        const val INITIAL_FAN_VERTICES = 64
    }
}

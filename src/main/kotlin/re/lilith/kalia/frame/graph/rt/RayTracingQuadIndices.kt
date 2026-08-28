package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The triangle indices every section's acceleration structure is built against.
 *
 * Chunk meshes are quads laid out four vertices at a time, so one shared index
 * buffer describes all of them. The terrain renderer keeps its own copy for
 * drawing; this one exists separately because a build has to read it through a
 * device address, which the drawing copy is not required to expose.
 *
 * A build reads the indices once and keeps its own representation, so growing
 * this buffer never invalidates a structure that was already built from it.
 */
internal class RayTracingQuadIndices(private val device: RenderDevice) : AutoCloseable {
    var buffer: GpuBuffer? = null
        private set

    private var quadCapacity = 0

    /**
     * Grows the buffer so it can describe at least [quads] quads.
     */
    fun ensure(quads: Int) {
        if (quads <= quadCapacity && buffer != null) {
            return
        }

        val target = maxOf(quads, quadCapacity * 2, MINIMUM_QUADS)
        val created = device.createBuffer(
            BufferDescription(
                label = "kalia-rt-quad-indices",
                sizeBytes = target.toLong() * INDICES_PER_QUAD * Int.SIZE_BYTES,
                usage = BufferUsage.STATIC,
                index = true,
                rayTracingInput = true,
            ),
        )
        created.write(build(target))

        buffer?.close()
        buffer = created
        quadCapacity = target
    }

    private fun build(quads: Int): ByteBuffer {
        val data = ByteBuffer
            .allocateDirect(quads * INDICES_PER_QUAD * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        for (quad in 0 until quads) {
            val vertex = quad * 4
            // The same winding the chunk renderer uses, which the trace shader
            // relies on to recover a triangle's vertices without reading indices.
            data.putInt(vertex + 0)
            data.putInt(vertex + 1)
            data.putInt(vertex + 2)
            data.putInt(vertex + 2)
            data.putInt(vertex + 3)
            data.putInt(vertex + 0)
        }

        data.flip()
        return data
    }

    override fun close() {
        buffer?.close()
        buffer = null
        quadCapacity = 0
    }

    private companion object {
        const val INDICES_PER_QUAD = 6
        const val MINIMUM_QUADS = 1 shl 14
    }
}

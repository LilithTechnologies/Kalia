package re.lilith.kalia.renderer.resource

import java.nio.ByteBuffer

interface GpuBuffer : GpuResource {
    val sizeBytes: Long
    val usage: BufferUsage

    /**
     * Writes [source] into this buffer at [offsetBytes]
     */
    fun write(source: ByteBuffer, offsetBytes: Long = 0L)

    /**
     * Writes [source] into this buffer at 0
     */
    fun write(source: ByteBuffer) = write(source, 0L)

    /**
     * The persistently mapped contents of a [BufferUsage.STREAM] buffer
     */
    fun mapped(): ByteBuffer?
}

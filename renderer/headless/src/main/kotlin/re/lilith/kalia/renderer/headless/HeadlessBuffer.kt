package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class HeadlessBuffer(
    override val label: String,
    override val sizeBytes: Long,
    override val usage: BufferUsage,
) : GpuBuffer {
    private var closed = false

    override val isClosed: Boolean
        get() = closed

    override fun mapped(): ByteBuffer? {
        check(!closed)
        return null
    }

    override fun write(source: ByteBuffer, offsetBytes: Long) {
        check(!closed)

        val length = source.remaining().toLong()

        require(offsetBytes >= 0)
        require(offsetBytes + length <= sizeBytes) {
            "Write of $length bytes at $offsetBytes overflows buffer '$label' ($sizeBytes bytes)."
        }
    }

    override fun close() {
        closed = true
    }
}
package re.lilith.kalia.buffer

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer

class StreamArena(
    private val device: RenderDevice,
    private val label: String,
    private val vertex: Boolean = true,
    private val index: Boolean = false,
    private val pageBytes: Long = DEFAULT_PAGE_BYTES,
) : AutoCloseable {
    private val pages = mutableListOf<Page>()
    private var pageIndex = 0

    val allocatedBytes: Long
        get() = pages.sumOf(Page::capacity)

    fun append(source: ByteBuffer, byteCount: Int): Slice {
        require(byteCount >= 0) { "byteCount must be >= 0." }
        if (byteCount == 0) {
            val page = pageFor(0)
            return Slice(page.buffer, page.used, 0)
        }

        var page = pageFor(byteCount)
        if (page.used + byteCount > page.capacity) {
            pageIndex++
            page = pageFor(byteCount)
        }

        val offset = page.used
        val limit = source.limit()
        try {
            source.limit(source.position() + byteCount)
            page.buffer.write(source, offset)
        } finally {
            source.limit(limit)
        }
        page.used += byteCount.toLong()
        return Slice(page.buffer, offset, byteCount)
    }

    fun reset() {
        val lastUsed = pages.indexOfLast { it.used > 0L }
        while (pages.size > lastUsed + 1) {
            pages.removeLast().buffer.close()
        }
        pages.forEach { it.used = 0L }
        pageIndex = 0
    }

    override fun close() {
        pages.forEach { it.buffer.close() }
        pages.clear()
        pageIndex = 0
    }

    private fun pageFor(requiredBytes: Int): Page {
        while (pages.size <= pageIndex) {
            pages += newPage(capacityFor(requiredBytes))
        }

        val required = capacityFor(requiredBytes)
        val existing = pages[pageIndex]
        if (existing.capacity >= required) {
            return existing
        }

        existing.buffer.close()
        return newPage(required).also { pages[pageIndex] = it }
    }

    private fun newPage(capacity: Long): Page = Page(
        buffer = device.createBuffer(
            BufferDescription(
                label = "$label/page${pages.size}",
                sizeBytes = capacity,
                usage = BufferUsage.STREAM,
                vertex = vertex,
                index = index,
            ),
        ),
        capacity = capacity,
    )

    private fun capacityFor(requiredBytes: Int): Long {
        var capacity = pageBytes
        while (capacity < requiredBytes.toLong()) {
            capacity = capacity shl 1
        }
        return capacity
    }

    class Slice(val buffer: GpuBuffer, val offsetBytes: Long, val sizeBytes: Int)

    private class Page(val buffer: GpuBuffer, val capacity: Long, var used: Long = 0L)

    private companion object {
        const val DEFAULT_PAGE_BYTES = 4L * 1024L * 1024L
    }
}

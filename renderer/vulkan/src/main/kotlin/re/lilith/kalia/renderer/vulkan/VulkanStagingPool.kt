package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import re.lilith.vulkan.api.memory.Buffer as VkBuffer

internal class VulkanStagingPool(private val context: VulkanContext) : AutoCloseable {
    private val free = ArrayDeque<Page>()
    private val open = mutableListOf<Page>()
    private var current: Page? = null

    var reservedOffset: Long = 0L
        private set

    private class Page(val buffer: VkBuffer, val capacity: Long) {
        var used: Long = 0L
    }

    fun reserve(length: Long): VkBuffer {
        val aligned = (length + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT

        val existing = current
        if (existing != null && existing.used + aligned <= existing.capacity) {
            reservedOffset = existing.used
            existing.used += aligned
            return existing.buffer
        }

        val page = acquire(aligned)
        open += page
        current = page
        reservedOffset = 0L
        page.used = aligned
        return page.buffer
    }

    fun write(source: ByteBuffer, length: Long): VkBuffer {
        re.lilith.kalia.renderer.device.RenderStats.recordUpload(length)
        val buffer = reserve(length)
        val destination = buffer.mappedAddress + reservedOffset
        if (source.isDirect) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), destination, length)
        } else {
            MemoryUtil.memByteBuffer(destination, length.toInt()).put(source.duplicate())
        }
        return buffer
    }

    private fun acquire(required: Long): Page {
        if (required <= PAGE_BYTES) {
            val recycled = free.removeLastOrNull()
            if (recycled != null) {
                recycled.used = 0L
                return recycled
            }
        }
        val capacity = maxOf(required, PAGE_BYTES)
        return Page(context.createStagingBuffer(capacity), capacity)
    }

    fun endBatch(): AutoCloseable {
        val batch = open.toList()
        open.clear()
        current = null
        return AutoCloseable {
            for (page in batch) {
                page.used = 0L
                if (page.capacity == PAGE_BYTES && free.size < MAX_FREE_PAGES) {
                    free.addLast(page)
                } else {
                    page.buffer.close()
                }
            }
        }
    }

    override fun close() {
        free.forEach { it.buffer.close() }
        free.clear()
        open.forEach { it.buffer.close() }
        open.clear()
        current = null
    }

    private companion object {
        const val PAGE_BYTES = 4L * 1024L * 1024L
        const val ALIGNMENT = 16L
        const val MAX_FREE_PAGES = 8
    }
}

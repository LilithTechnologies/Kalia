package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryUtil
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.command.beginQuery
import re.lilith.vulkan.api.command.endQuery
import re.lilith.vulkan.api.command.resetQueries
import re.lilith.vulkan.api.query.QueryPool
import re.lilith.vulkan.api.query.QueryPoolConfig
import java.nio.ByteBuffer

internal class VulkanOcclusionQueries(
    context: VulkanContext,
    val capacity: Int = DEFAULT_CAPACITY,
    private val slots: Int = 1,
) : AutoCloseable {
    private val pool: QueryPool = context.device.createQueryPool(
        QueryPoolConfig(capacity = capacity * slots),
    )

    private val results: ByteBuffer = MemoryUtil.memAlloc(capacity * QueryPool.RESULT_STRIDE.toInt())
    private val known = LongArray(capacity) { UNKNOWN }

    private var slot = 0
    private var issued = 0
    private var pendingSlot = -1
    private var pendingCount = 0

    fun beginFrame(count: Int) {
        collect()
        slot = (slot + 1) % slots
        issued = count.coerceIn(0, capacity)
    }

    fun reset(recorder: CommandRecorder) {
        if (issued == 0) {
            return
        }
        recorder.resetQueries(pool, slot * capacity, issued)
    }

    fun begin(recorder: CommandRecorder, index: Int) {
        if (index !in 0 until issued) {
            return
        }
        recorder.beginQuery(pool, slot * capacity + index)
    }

    fun end(recorder: CommandRecorder, index: Int) {
        if (index !in 0 until issued) {
            return
        }
        recorder.endQuery(pool, slot * capacity + index)
    }

    fun submitted() {
        if (issued == 0) {
            return
        }
        pendingSlot = slot
        pendingCount = issued
    }

    fun resultOf(index: Int): Long = if (index in 0 until capacity) known[index] else UNKNOWN

    private fun collect() {
        if (pendingSlot < 0 || pendingCount == 0) {
            return
        }
        results.clear()
        if (pool.results(pendingSlot * capacity, pendingCount, results, wait = false)) {
            for (index in 0 until pendingCount) {
                known[index] = results.getLong(index * QueryPool.RESULT_STRIDE.toInt())
            }
        }
        pendingSlot = -1
        pendingCount = 0
    }

    override fun close() {
        MemoryUtil.memFree(results)
        pool.close()
    }

    companion object {
        const val UNKNOWN = -1L
        const val DEFAULT_CAPACITY = 1024
    }
}

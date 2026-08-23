package re.lilith.vulkan.api.command

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.query.QueryPool

fun CommandRecorder.resetQueries(pool: QueryPool, first: Int, count: Int): CommandRecorder = apply {
    require(first >= 0 && count > 0) { "Query range must be positive." }
    require(first + count <= pool.capacity) { "Query range exceeds the pool capacity." }
    VK10.vkCmdResetQueryPool(commandBuffer.handle, pool.handle, first, count)
}

fun CommandRecorder.beginQuery(pool: QueryPool, index: Int, precise: Boolean = false): CommandRecorder = apply {
    require(index in 0 until pool.capacity) { "Query index is out of range." }
    VK10.vkCmdBeginQuery(
        commandBuffer.handle,
        pool.handle,
        index,
        if (precise) VK10.VK_QUERY_CONTROL_PRECISE_BIT else 0,
    )
}

fun CommandRecorder.endQuery(pool: QueryPool, index: Int): CommandRecorder = apply {
    require(index in 0 until pool.capacity) { "Query index is out of range." }
    VK10.vkCmdEndQuery(commandBuffer.handle, pool.handle, index)
}

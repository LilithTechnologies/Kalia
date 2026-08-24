package re.lilith.vulkan.api.query

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.resource.VulkanResource
import java.nio.ByteBuffer

class QueryPool internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val capacity: Int,
) : VulkanResource() {
    fun results(first: Int, count: Int, target: ByteBuffer, wait: Boolean = false): Boolean {
        if (count <= 0) {
            return true
        }
        val flags = VK10.VK_QUERY_RESULT_64_BIT or
                if (wait) VK10.VK_QUERY_RESULT_WAIT_BIT else 0
        val result = VK10.vkGetQueryPoolResults(
            device.handle,
            handle,
            first,
            count,
            target,
            RESULT_STRIDE,
            flags,
        )
        return result == VK10.VK_SUCCESS
    }

    override fun closeResource() {
        VK10.vkDestroyQueryPool(device.handle, handle, null)
    }

    companion object {
        const val RESULT_STRIDE = 8L
    }
}

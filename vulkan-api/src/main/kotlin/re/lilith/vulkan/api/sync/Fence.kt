package re.lilith.vulkan.api.sync

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.resource.VulkanResource

class Fence internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
) : VulkanResource() {
    fun wait(timeoutNanos: Long = Long.MAX_VALUE): Boolean =
        VK10.vkWaitForFences(device.handle, handle, true, timeoutNanos) == VK10.VK_SUCCESS

    fun reset() {
        checkVulkanResult(VK10.vkResetFences(device.handle, handle), "Resetting fence")
    }

    fun isSignaled(): Boolean = VK10.vkGetFenceStatus(device.handle, handle) != VK10.VK_NOT_READY

    override fun closeResource() {
        VK10.vkDestroyFence(device.handle, handle, null)
    }
}

package re.lilith.vulkan.api.sync

import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice

class BinarySemaphore internal constructor(
    device: LogicalDevice,
    handle: Long,
) : Semaphore(device, handle) {
    override fun closeResource() {
        VK10.vkDestroySemaphore(device.handle, handle, null)
    }
}
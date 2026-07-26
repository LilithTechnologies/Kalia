package re.lilith.vulkan.api.presentation

import org.lwjgl.vulkan.KHRSurface
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.resource.VulkanResource

class Surface internal constructor(
    internal val instance: VulkanInstance,
    internal val handle: Long,
) : VulkanResource() {
    override fun closeResource() {
        KHRSurface.vkDestroySurfaceKHR(instance.handle, handle, null)
    }
}
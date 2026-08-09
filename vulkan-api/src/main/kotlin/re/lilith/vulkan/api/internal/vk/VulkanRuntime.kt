package re.lilith.vulkan.api.internal.vk

import org.lwjgl.vulkan.VK
import re.lilith.vulkan.api.core.Version

internal object VulkanRuntime {
    val supportedApiVersion: Version
        get() = Version.decode(VK.getInstanceVersionSupported())
}


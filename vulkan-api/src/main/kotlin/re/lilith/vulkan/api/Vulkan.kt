package re.lilith.vulkan.api

import org.lwjgl.system.Configuration
import re.lilith.vulkan.api.core.Version
import re.lilith.vulkan.api.instance.InstanceConfigBuilder
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.internal.vk.VulkanRuntime

object Vulkan {
    val supportedApiVersion: Version
        get() = VulkanRuntime.supportedApiVersion

    fun createInstance(configure: InstanceConfigBuilder.() -> Unit = {}): VulkanInstance {
        Configuration.STACK_SIZE.set(256) // or it'll crash on nv drivers
        return VulkanInstance.create(InstanceConfigBuilder().apply(configure).build())
    }
}
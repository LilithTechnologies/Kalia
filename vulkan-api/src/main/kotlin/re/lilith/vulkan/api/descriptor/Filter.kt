package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.VK10

enum class Filter(internal val vkValue: Int) {
    Nearest(VK10.VK_FILTER_NEAREST),
    Linear(VK10.VK_FILTER_LINEAR),
}

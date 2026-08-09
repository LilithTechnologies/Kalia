package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class CullMode(internal val vkValue: Int) {
    None(VK10.VK_CULL_MODE_NONE),
    Front(VK10.VK_CULL_MODE_FRONT_BIT),
    Back(VK10.VK_CULL_MODE_BACK_BIT),
    FrontAndBack(VK10.VK_CULL_MODE_FRONT_AND_BACK),
}
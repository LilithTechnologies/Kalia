package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.VK10

enum class SamplerMipmapMode(internal val vkValue: Int) {
    Nearest(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST),
    Linear(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR),
}

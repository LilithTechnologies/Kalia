package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.VK10

enum class SamplerAddressMode(internal val vkValue: Int) {
    Repeat(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT),
    MirroredRepeat(VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT),
    ClampToEdge(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE),
    ClampToBorder(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER),
}

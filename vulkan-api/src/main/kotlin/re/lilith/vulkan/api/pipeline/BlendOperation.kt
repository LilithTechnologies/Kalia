package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class BlendOperation(internal val vkValue: Int) {
    Add(VK10.VK_BLEND_OP_ADD),
    Subtract(VK10.VK_BLEND_OP_SUBTRACT),
    ReverseSubtract(VK10.VK_BLEND_OP_REVERSE_SUBTRACT),
    Minimum(VK10.VK_BLEND_OP_MIN),
    Maximum(VK10.VK_BLEND_OP_MAX),
}


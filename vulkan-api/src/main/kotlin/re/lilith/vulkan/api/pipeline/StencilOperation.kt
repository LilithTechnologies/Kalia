package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class StencilOperation(internal val vkValue: Int) {
    Keep(VK10.VK_STENCIL_OP_KEEP),
    Zero(VK10.VK_STENCIL_OP_ZERO),
    Replace(VK10.VK_STENCIL_OP_REPLACE),
    IncrementAndClamp(VK10.VK_STENCIL_OP_INCREMENT_AND_CLAMP),
    DecrementAndClamp(VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP),
    Invert(VK10.VK_STENCIL_OP_INVERT),
    IncrementAndWrap(VK10.VK_STENCIL_OP_INCREMENT_AND_WRAP),
    DecrementAndWrap(VK10.VK_STENCIL_OP_DECREMENT_AND_WRAP),
}


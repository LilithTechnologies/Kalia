package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class DynamicState(internal val vkValue: Int) {
    Viewport(VK10.VK_DYNAMIC_STATE_VIEWPORT),
    Scissor(VK10.VK_DYNAMIC_STATE_SCISSOR),
    LineWidth(VK10.VK_DYNAMIC_STATE_LINE_WIDTH),
    DepthBias(VK10.VK_DYNAMIC_STATE_DEPTH_BIAS),
    BlendConstants(VK10.VK_DYNAMIC_STATE_BLEND_CONSTANTS),
    DepthBounds(VK10.VK_DYNAMIC_STATE_DEPTH_BOUNDS),
    StencilCompareMask(VK10.VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK),
    StencilWriteMask(VK10.VK_DYNAMIC_STATE_STENCIL_WRITE_MASK),
    StencilReference(VK10.VK_DYNAMIC_STATE_STENCIL_REFERENCE),
}


package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class BlendFactor(internal val vkValue: Int) {
    Zero(VK10.VK_BLEND_FACTOR_ZERO),
    One(VK10.VK_BLEND_FACTOR_ONE),
    SourceColor(VK10.VK_BLEND_FACTOR_SRC_COLOR),
    OneMinusSourceColor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR),
    DestinationColor(VK10.VK_BLEND_FACTOR_DST_COLOR),
    OneMinusDestinationColor(VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR),
    SourceAlpha(VK10.VK_BLEND_FACTOR_SRC_ALPHA),
    OneMinusSourceAlpha(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA),
    DestinationAlpha(VK10.VK_BLEND_FACTOR_DST_ALPHA),
    OneMinusDestinationAlpha(VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA),
    SourceAlphaSaturate(VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE),
    ConstantColor(VK10.VK_BLEND_FACTOR_CONSTANT_COLOR),
    OneMinusConstantColor(VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR),
    ConstantAlpha(VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA),
    OneMinusConstantAlpha(VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA),
}


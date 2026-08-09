package re.lilith.vulkan.api.presentation

import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR

enum class ColorSpace(internal val vkValue: Int) {
    SrgbNonLinear(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR),
}
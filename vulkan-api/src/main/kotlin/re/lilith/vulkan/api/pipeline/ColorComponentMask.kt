package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

@JvmInline
value class ColorComponentMask internal constructor(internal val vkBits: Int) {
    operator fun plus(other: ColorComponentMask): ColorComponentMask = ColorComponentMask(vkBits or other.vkBits)

    companion object {
        val None = ColorComponentMask(0)
        val Red = ColorComponentMask(VK10.VK_COLOR_COMPONENT_R_BIT)
        val Green = ColorComponentMask(VK10.VK_COLOR_COMPONENT_G_BIT)
        val Blue = ColorComponentMask(VK10.VK_COLOR_COMPONENT_B_BIT)
        val Alpha = ColorComponentMask(VK10.VK_COLOR_COMPONENT_A_BIT)
        val All = Red + Green + Blue + Alpha
    }
}


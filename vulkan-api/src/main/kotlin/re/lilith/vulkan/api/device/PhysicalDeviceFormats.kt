package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkFormatProperties
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.types.enum.Format

fun PhysicalDevice.findSupportedDepthFormat(prefer24Bit: Boolean = true): Format {
    val candidates = if (prefer24Bit) {
        listOf(Format.D24_UNorm_S8_UInt, Format.D32_SFloat, Format.D32_SFloat_S8_UInt)
    } else {
        listOf(Format.D32_SFloat, Format.D32_SFloat_S8_UInt, Format.D24_UNorm_S8_UInt)
    }

    var supported: Format? = null

    pushStack { stack ->
        val properties = VkFormatProperties.calloc(stack)
        for (format in candidates) {
            VK10.vkGetPhysicalDeviceFormatProperties(handle, format.vkValue, properties)
            if ((properties.optimalTilingFeatures() and VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                supported = format
                break
            }
        }
    }

    return supported ?: error("No supported depth format was found for physical device ${this.properties.name}.")
}

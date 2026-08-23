package re.lilith.vulkan.api.command

import org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY
import org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY

/**
 * The level of the command buffer.
 */
enum class CommandBufferLevel(internal val vkValue: Int) {
    /**
     * Primary command buffers are submitted to a queue for execution by the device.
     */
    PRIMARY(VK_COMMAND_BUFFER_LEVEL_PRIMARY),

    /**
     * Secondary command buffers may be allocated and recorded in parallel which
     * allows for better utilisation of modern hardware with its panoply of CPU cores.
     */
    SECONDARY(VK_COMMAND_BUFFER_LEVEL_SECONDARY),
}

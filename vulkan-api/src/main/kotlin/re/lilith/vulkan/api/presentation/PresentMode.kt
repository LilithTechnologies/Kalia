package re.lilith.vulkan.api.presentation

import org.lwjgl.vulkan.KHRSurface.*

enum class PresentMode(internal val vkValue: Int) {
    Immediate(VK_PRESENT_MODE_IMMEDIATE_KHR),
    Mailbox(VK_PRESENT_MODE_MAILBOX_KHR),
    Fifo(VK_PRESENT_MODE_FIFO_KHR),
    FifoRelaxed(VK_PRESENT_MODE_FIFO_RELAXED_KHR),
}

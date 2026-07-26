package re.lilith.vulkan.api.command

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkClearAttachment
import org.lwjgl.vulkan.VkClearRect
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.geometry.Rect2D

/**
 * One attachment to clear inside an active render pass.
 *
 * [colorAttachmentIndex] is only read for colour clears; depth and stencil clears always
 * target the single depth-stencil attachment.
 */
data class AttachmentClear(
    val value: ClearValue,
    val colorAttachmentIndex: Int = 0,
    val clearDepth: Boolean = true,
    val clearStencil: Boolean = false,
)

/**
 * Clears regions of the attachments bound to the active render pass.
 *
 * Unlike a load-op clear this works part-way through a pass, which is what an application
 * built around an imperative `glClear` needs. It is more expensive than a load-op clear, so
 * prefer clearing at pass entry wherever the pass structure allows it.
 */
fun CommandRecorder.clearAttachments(
    clears: List<AttachmentClear>,
    area: Rect2D,
    baseArrayLayer: Int = 0,
    layerCount: Int = 1,
): CommandRecorder = apply {
    if (clears.isEmpty()) {
        return@apply
    }

    pushStack { stack ->
        val attachments = VkClearAttachment.calloc(clears.size, stack)
        clears.forEachIndexed { index, clear ->
            val attachment = attachments[index]
            when (val value = clear.value) {
                is ClearValue.Color -> {
                    attachment
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .colorAttachment(clear.colorAttachmentIndex)
                    attachment.clearValue().color()
                        .float32(0, value.value.red)
                        .float32(1, value.value.green)
                        .float32(2, value.value.blue)
                        .float32(3, value.value.alpha)
                }

                is ClearValue.DepthStencil -> {
                    var aspect = 0
                    if (clear.clearDepth) aspect = aspect or VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                    if (clear.clearStencil) aspect = aspect or VK10.VK_IMAGE_ASPECT_STENCIL_BIT
                    attachment.aspectMask(aspect)
                    attachment.clearValue().depthStencil()
                        .depth(value.value.depth)
                        .stencil(value.value.stencil)
                }
            }
        }

        val rects = VkClearRect.calloc(1, stack)
        rects[0]
            .baseArrayLayer(baseArrayLayer)
            .layerCount(layerCount)
            .rect { rect ->
                rect.offset().set(area.offset.x, area.offset.y)
                rect.extent().set(area.extent.width, area.extent.height)
            }

        VK10.vkCmdClearAttachments(commandBuffer.handle, attachments, rects)
    }
}

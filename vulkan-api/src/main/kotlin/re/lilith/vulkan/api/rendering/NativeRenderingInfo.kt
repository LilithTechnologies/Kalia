package re.lilith.vulkan.api.rendering

import org.lwjgl.vulkan.KHRDynamicRendering
import org.lwjgl.vulkan.VK13
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkRenderingAttachmentInfo
import org.lwjgl.vulkan.VkRenderingInfo
import re.lilith.vulkan.api.internal.vk.VulkanConstants
import re.lilith.vulkan.api.types.clear.ClearValue

class NativeRenderingInfo internal constructor(
    info: RenderingInfo,
    useDynamicRenderingExtension: Boolean,
) : AutoCloseable {
    private val attachmentType = if (useDynamicRenderingExtension) {
        KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR
    } else {
        VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO
    }

    private val colorAttachments =
        info.colorAttachments.takeIf(List<*>::isNotEmpty)?.let { attachments ->
            VkRenderingAttachmentInfo.calloc(attachments.size).also { buffer ->
                attachments.forEachIndexed { index, attachment -> buffer[index].populate(attachment) }
            }
        }

    private val depthAttachment =
        info.depthAttachment?.let { VkRenderingAttachmentInfo.calloc().populate(it) }

    private val stencilAttachment =
        info.stencilAttachment?.let { VkRenderingAttachmentInfo.calloc().populate(it) }

    private val struct = VkRenderingInfo.calloc()
        .sType(
            if (useDynamicRenderingExtension) {
                KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR
            } else {
                VK13.VK_STRUCTURE_TYPE_RENDERING_INFO
            },
        )
        .layerCount(info.layerCount)
        .viewMask(info.viewMask.bits)
        .apply {
            renderArea { area ->
                area.offset().set(info.renderArea.offset.x, info.renderArea.offset.y)
                area.extent().set(info.renderArea.extent.width, info.renderArea.extent.height)
            }
            if (colorAttachments != null) {
                pColorAttachments(colorAttachments)
            }
            depthAttachment?.let(::pDepthAttachment)
            stencilAttachment?.let(::pStencilAttachment)
        }

    val address get() = struct.address()

    private fun VkRenderingAttachmentInfo.populate(
        attachment: RenderingAttachmentInfo,
    ) = sType(attachmentType)
        .imageView(attachment.imageView.nativeHandle)
        .imageLayout(attachment.imageLayout.vkValue)
        .resolveMode(VulkanConstants.ResolveModes.none)
        .loadOp(attachment.loadOperation.vkValue)
        .storeOp(attachment.storeOperation.vkValue)
        .also { info ->
            if (attachment.resolveImageView != null) {
                info.resolveImageView(attachment.resolveImageView.nativeHandle)
                    .resolveImageLayout(attachment.resolveImageLayout.vkValue)
            }
            attachment.clearValue?.let { clear -> info.clearValue().populate(clear) }
        }

    private fun VkClearValue.populate(clearValue: ClearValue) {
        when (clearValue) {
            is ClearValue.Color -> {
                color().float32(0, clearValue.value.red)
                color().float32(1, clearValue.value.green)
                color().float32(2, clearValue.value.blue)
                color().float32(3, clearValue.value.alpha)
            }

            is ClearValue.DepthStencil -> {
                depthStencil().depth(clearValue.value.depth)
                depthStencil().stencil(clearValue.value.stencil)
            }
        }
    }

    override fun close() {
        struct.free()
        colorAttachments?.free()
        depthAttachment?.free()
        stencilAttachment?.free()
    }
}
package re.lilith.vulkan.api.command

import org.lwjgl.system.MemoryUtil.memPutLong
import org.lwjgl.system.MemoryUtil.nmemAlloc
import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.internal.vk.VulkanConstants
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.rendering.*
import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.enum.IndexType
import re.lilith.vulkan.api.types.enum.SubpassContents
import re.lilith.vulkan.api.types.geometry.Rect2D
import re.lilith.vulkan.api.types.geometry.Viewport
import re.lilith.vulkan.api.types.transfer.BufferCopy

private val pBuffers = nmemAlloc(8) // one VkBuffer handle
private val pOffsets = nmemAlloc(8) // one VkDeviceSize

class CommandRecorder internal constructor(
    val commandBuffer: CommandBuffer,
) {
    fun setLineWidth(lineWidth: Float) {
        VK10.vkCmdSetLineWidth(commandBuffer.handle, lineWidth)
    }

    fun setDepthBias(constantFactor: Float, clamp: Float, slopeFactor: Float) {
        VK10.vkCmdSetDepthBias(commandBuffer.handle, constantFactor, clamp, slopeFactor)
    }

    fun setViewport(viewport: Viewport) = apply {
        pushStack { stack ->
            val viewportBuffer = VkViewport.calloc(1, stack)
            viewportBuffer[0]
                .x(viewport.x)
                .y(viewport.y)
                .width(viewport.width)
                .height(viewport.height)
                .minDepth(viewport.minDepth)
                .maxDepth(viewport.maxDepth)
            VK10.vkCmdSetViewport(commandBuffer.handle, 0, viewportBuffer)
        }
    }

    fun setScissor(scissor: Rect2D) = apply {
        pushStack { stack ->
            val scissorBuffer = VkRect2D.calloc(1, stack)
            scissorBuffer[0]
                .offset { it.set(scissor.offset.x, scissor.offset.y) }
                .extent { it.set(scissor.extent.width, scissor.extent.height) }
            VK10.vkCmdSetScissor(commandBuffer.handle, 0, scissorBuffer)
        }
    }

    fun bindVertexBuffer(binding: Int, buffer: Buffer, offset: Long = 0L) = apply {
        memPutLong(pBuffers, buffer.handle)
        memPutLong(pOffsets, offset)

        VK10.nvkCmdBindVertexBuffers(
            commandBuffer.handle,
            binding,
            1,
            pBuffers,
            pOffsets
        )
    }

    fun bindIndexBuffer(buffer: Buffer, offset: Long = 0L, indexType: IndexType = IndexType.UnsignedInt) = apply {
        VK10.vkCmdBindIndexBuffer(commandBuffer.handle, buffer.handle, offset, indexType.vkValue)
    }

    fun copyBuffer(source: Buffer, destination: Buffer, regions: List<BufferCopy>) = apply {
        pushStack { stack ->
            val copies = VkBufferCopy.calloc(regions.size, stack)
            regions.forEachIndexed { index, region ->
                copies[index]
                    .srcOffset(region.sourceOffset)
                    .dstOffset(region.destinationOffset)
                    .size(region.size)
            }
            VK10.vkCmdCopyBuffer(commandBuffer.handle, source.handle, destination.handle, copies)
        }
    }

    fun beginRendering(info: RenderingInfo, action: CommandRecorder.() -> Unit) = apply {
        beginRendering(info).apply(action)
    }

    fun beginRendering(info: RenderingInfo) = apply {
        pushStack { stack ->
            val useDynamicRenderingExtension =
                commandBuffer.device.physicalDevice.instance.config.applicationInfo.apiVersion < re.lilith.vulkan.api.core.Version.V1_3 &&
                        KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME in commandBuffer.device.enabledExtensions
            val colorAttachments = VkRenderingAttachmentInfo.calloc(info.colorAttachments.size, stack)
            info.colorAttachments.forEachIndexed { index, attachment ->
                colorAttachments[index]
                    .populate(attachment)
                    .sType(
                        if (useDynamicRenderingExtension) {
                            KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR
                        } else {
                            VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO
                        },
                    )
            }

            val depthAttachment = info.depthAttachment?.let {
                VkRenderingAttachmentInfo.calloc(stack)
                    .populate(it)
                    .sType(
                        if (useDynamicRenderingExtension) {
                            KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR
                        } else {
                            VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO
                        },
                    )
            }
            val stencilAttachment = info.stencilAttachment?.let {
                VkRenderingAttachmentInfo.calloc(stack)
                    .populate(it)
                    .sType(
                        if (useDynamicRenderingExtension) {
                            KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR
                        } else {
                            VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO
                        },
                    )
            }

            val renderingInfo = VkRenderingInfo.calloc(stack)
                .sType(
                    if (useDynamicRenderingExtension) {
                        KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR
                    } else {
                        VK13.VK_STRUCTURE_TYPE_RENDERING_INFO
                    },
                )
                .renderArea { renderArea ->
                    renderArea.offset().set(info.renderArea.offset.x, info.renderArea.offset.y)
                    renderArea.extent().set(info.renderArea.extent.width, info.renderArea.extent.height)
                }
                .layerCount(info.layerCount)
                .viewMask(info.viewMask.bits)
                .pColorAttachments(colorAttachments)

            if (depthAttachment != null) {
                renderingInfo.pDepthAttachment(depthAttachment)
            }
            if (stencilAttachment != null) {
                renderingInfo.pStencilAttachment(stencilAttachment)
            }

            if (useDynamicRenderingExtension) {
                KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer.handle, renderingInfo)
            } else {
                VK13.vkCmdBeginRendering(commandBuffer.handle, renderingInfo)
            }
        }
    }

    fun endRendering() = apply {
        val useDynamicRenderingExtension =
            commandBuffer.device.physicalDevice.instance.config.applicationInfo.apiVersion < re.lilith.vulkan.api.core.Version.V1_3 &&
                    KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME in commandBuffer.device.enabledExtensions
        if (useDynamicRenderingExtension) {
            KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer.handle)
        } else {
            VK13.vkCmdEndRendering(commandBuffer.handle)
        }
    }

    fun beginRenderPass(
        renderPass: RenderPass,
        framebuffer: Framebuffer,
        renderArea: Rect2D,
        clearValues: List<ClearValue> = emptyList(),
        contents: SubpassContents = SubpassContents.Inline,
    ) = apply {
        pushStack { stack ->
            val clearBuffer = VkClearValue.calloc(clearValues.size, stack)
            clearValues.forEachIndexed { index, clearValue ->
                clearBuffer[index].populate(clearValue)
            }

            val beginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass.handle)
                .framebuffer(framebuffer.handle)
                .renderArea { area ->
                    area.offset().set(renderArea.offset.x, renderArea.offset.y)
                    area.extent().set(renderArea.extent.width, renderArea.extent.height)
                }
                .pClearValues(clearBuffer)

            VK10.vkCmdBeginRenderPass(commandBuffer.handle, beginInfo, contents.vkValue)
        }
    }

    fun nextSubpass(contents: SubpassContents = SubpassContents.Inline) = apply {
        VK10.vkCmdNextSubpass(commandBuffer.handle, contents.vkValue)
    }

    fun endRenderPass() = apply {
        VK10.vkCmdEndRenderPass(commandBuffer.handle)
    }

    fun draw(
        vertexCount: Int,
        instanceCount: Int = 1,
        firstVertex: Int = 0,
        firstInstance: Int = 0,
    ) = apply {
        VK10.vkCmdDraw(commandBuffer.handle, vertexCount, instanceCount, firstVertex, firstInstance)
    }

    fun drawIndexed(
        indexCount: Int,
        instanceCount: Int = 1,
        firstIndex: Int = 0,
        vertexOffset: Int = 0,
        firstInstance: Int = 0,
    ) = apply {
        VK10.vkCmdDrawIndexed(commandBuffer.handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)
    }

    fun dispatch(groupCountX: Int, groupCountY: Int = 1, groupCountZ: Int = 1) = apply {
        require(groupCountX > 0) { "groupCountX must be > 0." }
        require(groupCountY > 0) { "groupCountY must be > 0." }
        require(groupCountZ > 0) { "groupCountZ must be > 0." }
        VK10.vkCmdDispatch(commandBuffer.handle, groupCountX, groupCountY, groupCountZ)
    }

    fun end(): CommandBuffer = commandBuffer.also { it.endRecording() }

    private fun VkRenderingAttachmentInfo.populate(attachment: RenderingAttachmentInfo): VkRenderingAttachmentInfo =
        sType(VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO)
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
                attachment.clearValue?.let { clear ->
                    info.clearValue().populate(clear)
                }
            }

    private fun VkClearValue.populate(clearValue: ClearValue): VkClearValue = apply {
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
}








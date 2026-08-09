@file:Suppress("DEPRECATION")

package re.lilith.vulkan.api.device

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.rendering.*

internal object LogicalDeviceRendering {
    fun createRenderPass(device: LogicalDevice, layout: RenderPassLayout): RenderPass = pushStack { stack ->
        val attachments = VkAttachmentDescription.calloc(layout.attachments.size, stack)
        layout.attachments.forEachIndexed { index, attachment ->
            attachments[index]
                .format(attachment.format.vkValue)
                .samples(attachment.samples.vkValue)
                .loadOp(attachment.loadOperation.vkValue)
                .storeOp(attachment.storeOperation.vkValue)
                .stencilLoadOp(attachment.stencilLoadOperation.vkValue)
                .stencilStoreOp(attachment.stencilStoreOperation.vkValue)
                .initialLayout(attachment.initialLayout.vkValue)
                .finalLayout(attachment.finalLayout.vkValue)
        }

        val subpasses = VkSubpassDescription.calloc(layout.subpasses.size, stack)
        layout.subpasses.forEachIndexed { index, subpass ->
            val colors = attachmentReferences(stack, subpass.colorAttachments)
            val inputs = attachmentReferences(stack, subpass.inputAttachments)
            val resolves = attachmentReferences(stack, subpass.resolveAttachments)
            val depth = subpass.depthStencilAttachment?.let { reference ->
                VkAttachmentReference.calloc(stack)
                    .attachment(reference.attachmentIndex)
                    .layout(reference.layout.vkValue)
            }

            subpasses[index]
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(subpass.colorAttachments.size)
                .pColorAttachments(colors)
                .pInputAttachments(inputs)
                .pResolveAttachments(resolves)
                .pDepthStencilAttachment(depth)

            if (subpass.preserveAttachments.isNotEmpty()) {
                subpasses[index].pPreserveAttachments(stack.ints(*subpass.preserveAttachments.toIntArray()))
            }
        }

        val dependencies = VkSubpassDependency.calloc(layout.dependencies.size, stack)
        layout.dependencies.forEachIndexed { index, dependency ->
            dependencies[index]
                .srcSubpass(dependency.sourceSubpass.vkIndex)
                .dstSubpass(dependency.destinationSubpass.vkIndex)
                .srcStageMask(dependency.sourceStageMask.vkBits)
                .dstStageMask(dependency.destinationStageMask.vkBits)
                .srcAccessMask(dependency.sourceAccessMask.vkBits)
                .dstAccessMask(dependency.destinationAccessMask.vkBits)
                .dependencyFlags(dependency.dependencyFlags.vkBits)
        }

        val createInfo = VkRenderPassCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(attachments)
            .pSubpasses(subpasses)
            .pDependencies(dependencies)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateRenderPass(device.handle, createInfo, null, pointer), "Creating render pass")
        device.register(RenderPass(device, pointer[0], layout))
    }

    fun createFramebuffer(device: LogicalDevice, renderPass: RenderPass, config: FramebufferConfig): Framebuffer =
        pushStack { stack ->
            require(renderPass.device === device) { "Framebuffer render pass must belong to this logical device." }
            require(config.attachments.all { it.ownerDevice === device }) { "Framebuffer attachments must belong to this logical device." }

            val attachments = stack.mallocLong(config.attachments.size)
            config.attachments.forEachIndexed { index, imageView -> attachments.put(index, imageView.nativeHandle) }

            val createInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .renderPass(renderPass.handle)
                .pAttachments(attachments)
                .width(config.width)
                .height(config.height)
                .layers(config.layers)

            val pointer = stack.mallocLong(1)
            checkVulkanResult(
                VK10.vkCreateFramebuffer(device.handle, createInfo, null, pointer),
                "Creating framebuffer"
            )
            device.register(Framebuffer(device, pointer[0], renderPass, config))
        }
}

private fun attachmentReferences(
    stack: MemoryStack,
    references: List<AttachmentReference>,
): VkAttachmentReference.Buffer? {
    if (references.isEmpty()) {
        return null
    }

    val buffer = VkAttachmentReference.calloc(references.size, stack)
    references.forEachIndexed { index, reference ->
        buffer[index]
            .attachment(reference.attachmentIndex)
            .layout(reference.layout.vkValue)
    }
    return buffer
}





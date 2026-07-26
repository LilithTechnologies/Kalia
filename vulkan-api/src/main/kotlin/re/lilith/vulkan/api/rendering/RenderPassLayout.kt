package re.lilith.vulkan.api.rendering

data class RenderPassLayout(
    val attachments: List<AttachmentDescription>,
    val subpasses: List<SubpassDescription>,
    val dependencies: List<SubpassDependency> = emptyList(),
) {
    companion object {
        fun singleSubpass(
            attachments: List<AttachmentDescription>,
            colorAttachments: List<AttachmentReference> = emptyList(),
            inputAttachments: List<AttachmentReference> = emptyList(),
            resolveAttachments: List<AttachmentReference> = emptyList(),
            depthStencilAttachment: AttachmentReference? = null,
            preserveAttachments: List<Int> = emptyList(),
            dependencies: List<SubpassDependency> = emptyList(),
        ): RenderPassLayout = RenderPassLayout(
            attachments = attachments,
            subpasses = listOf(
                SubpassDescription.graphics(
                    colorAttachments = colorAttachments,
                    inputAttachments = inputAttachments,
                    resolveAttachments = resolveAttachments,
                    depthStencilAttachment = depthStencilAttachment,
                    preserveAttachments = preserveAttachments,
                ),
            ),
            dependencies = dependencies,
        )
    }
}

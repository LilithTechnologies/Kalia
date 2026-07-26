package re.lilith.vulkan.api.rendering

data class SubpassDescription(
    val colorAttachments: List<AttachmentReference> = emptyList(),
    val inputAttachments: List<AttachmentReference> = emptyList(),
    val resolveAttachments: List<AttachmentReference> = emptyList(),
    val depthStencilAttachment: AttachmentReference? = null,
    val preserveAttachments: List<Int> = emptyList(),
) {
    companion object {
        fun graphics(
            colorAttachments: List<AttachmentReference> = emptyList(),
            inputAttachments: List<AttachmentReference> = emptyList(),
            resolveAttachments: List<AttachmentReference> = emptyList(),
            depthStencilAttachment: AttachmentReference? = null,
            preserveAttachments: List<Int> = emptyList(),
        ): SubpassDescription = SubpassDescription(
            colorAttachments = colorAttachments,
            inputAttachments = inputAttachments,
            resolveAttachments = resolveAttachments,
            depthStencilAttachment = depthStencilAttachment,
            preserveAttachments = preserveAttachments,
        )
    }
}

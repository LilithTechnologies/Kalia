package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.types.geometry.Rect2D

data class RenderingInfo(
    val renderArea: Rect2D,
    val layerCount: Int = 1,
    val viewMask: ViewMask = ViewMask(),
    val colorAttachments: List<RenderingAttachmentInfo> = emptyList(),
    val depthAttachment: RenderingAttachmentInfo? = null,
    val stencilAttachment: RenderingAttachmentInfo? = null,
)


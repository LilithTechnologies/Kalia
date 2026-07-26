package re.lilith.vulkan.api.rendering

data class FramebufferConfig(
    val attachments: List<RenderingImageView>,
    val width: Int,
    val height: Int,
    val layers: Int = 1,
)
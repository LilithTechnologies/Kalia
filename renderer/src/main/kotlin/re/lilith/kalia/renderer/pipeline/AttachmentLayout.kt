package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.format.TextureFormat

/**
 * The attachment formats a pipeline is compiled against
 */
data class AttachmentLayout(
    val colorFormats: List<TextureFormat>,
    val depthFormat: TextureFormat? = null,
) {
    init {
        require(colorFormats.all(TextureFormat::isColor)) { "Colour attachments must use a colour format." }
        require(depthFormat?.isDepth != false) { "Depth attachment must use a depth format." }
    }
}

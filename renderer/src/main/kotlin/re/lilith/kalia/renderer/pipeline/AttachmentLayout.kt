package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.format.TextureFormat

/**
 * The attachment formats a pipeline is compiled against.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class AttachmentLayout(
    /**
     * A list of the formats used by the color attachments.
     */
    val colorFormats: List<TextureFormat>,

    /**
     * A list of the formats used by the depth attachments.
     */
    val depthFormat: TextureFormat? = null,
) {
    init {
        require(colorFormats.all(TextureFormat::isColor)) { "Color attachments must use a color format." }
        require(depthFormat?.isDepth != false) { "Depth attachment must use a depth format." }
    }
}

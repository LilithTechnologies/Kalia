package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.format.TextureFormat
import java.util.concurrent.ConcurrentHashMap

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

    private val cachedHashCode: Int = colorFormats.hashCode() * 31 + (depthFormat?.ordinal ?: -1)

    override fun hashCode(): Int = cachedHashCode

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentLayout) return false
        return cachedHashCode == other.cachedHashCode &&
                depthFormat == other.depthFormat &&
                colorFormats == other.colorFormats
    }

    companion object {
        private val interned = ConcurrentHashMap<AttachmentLayout, AttachmentLayout>()

        fun of(colorFormats: List<TextureFormat>, depthFormat: TextureFormat? = null): AttachmentLayout {
            val candidate = AttachmentLayout(colorFormats, depthFormat)
            return interned.putIfAbsent(candidate, candidate) ?: candidate
        }
    }
}

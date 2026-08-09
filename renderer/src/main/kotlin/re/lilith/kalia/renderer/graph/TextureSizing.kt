package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.geometry.Extent

/**
 * Determines how a graph texture's dimensions are calculated.
 *
 * @author Lunasa
 * @since 1.0.0
 */
sealed interface TextureSizing {
    /**
     * Uses a fixed pixel size.
     */
    data class Fixed(val extent: Extent) : TextureSizing

    /**
     * Scales with the back-buffer size.
     */
    data class RelativeToBackbuffer(val factor: Float) : TextureSizing {
        init {
            require(factor > 0f) { "Relative texture scale must be positive." }
        }
    }
}

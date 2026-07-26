package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.geometry.Extent

sealed interface TextureSizing {
    data class Fixed(val extent: Extent) : TextureSizing
    data class RelativeToBackbuffer(val factor: Float) : TextureSizing {
        init {
            require(factor > 0f) { "Relative texture scale must be positive." }
        }
    }
}
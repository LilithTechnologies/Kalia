package re.lilith.vulkan.api.pipeline

import re.lilith.vulkan.api.types.enum.Format

data class VertexInputAttribute(
    val location: Int,
    val binding: Int,
    val format: Format,
    val offset: Int,
) {
    init {
        require(location >= 0) { "location must be >= 0." }
        require(binding >= 0) { "binding must be >= 0." }
        require(offset >= 0) { "offset must be >= 0." }
    }
}


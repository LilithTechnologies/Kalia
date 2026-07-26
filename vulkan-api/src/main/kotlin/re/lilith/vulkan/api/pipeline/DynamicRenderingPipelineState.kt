package re.lilith.vulkan.api.pipeline

import re.lilith.vulkan.api.types.enum.Format

data class DynamicRenderingPipelineState(
    val colorFormats: List<Format>,
    val depthFormat: Format? = null,
    val stencilFormat: Format? = null,
) : PipelineRendering {
    init {
        require(colorFormats.isNotEmpty() || depthFormat != null || stencilFormat != null) {
            "Dynamic rendering must specify at least one color, depth, or stencil attachment format."
        }
    }
}

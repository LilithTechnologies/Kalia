package re.lilith.vulkan.api.pipeline

import re.lilith.vulkan.api.rendering.RenderPass

data class RenderPassPipelineState(
    val renderPass: RenderPass,
    val subpass: Int = 0,
) : PipelineRendering {
    init {
        require(subpass >= 0) { "subpass must be >= 0." }
    }
}


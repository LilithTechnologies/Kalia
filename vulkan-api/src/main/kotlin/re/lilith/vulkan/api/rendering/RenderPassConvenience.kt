package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.enum.SubpassContents
import re.lilith.vulkan.api.types.geometry.Rect2D

fun RenderPass.pipelineState(subpass: Int = 0): re.lilith.vulkan.api.pipeline.RenderPassPipelineState =
    re.lilith.vulkan.api.pipeline.RenderPassPipelineState(this, subpass)

inline fun CommandRecorder.renderPass(
    renderPass: RenderPass,
    framebuffer: Framebuffer,
    renderArea: Rect2D,
    clearValues: List<ClearValue> = emptyList(),
    contents: SubpassContents = SubpassContents.Inline,
    block: CommandRecorder.() -> Unit,
): CommandRecorder = apply {
    beginRenderPass(renderPass, framebuffer, renderArea, clearValues, contents)
    try {
        block()
    } finally {
        endRenderPass()
    }
}


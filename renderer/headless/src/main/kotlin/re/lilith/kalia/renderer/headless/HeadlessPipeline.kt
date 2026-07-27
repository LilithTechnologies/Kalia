package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.GpuPipeline

internal class HeadlessPipeline(
    override val label: String,
    val description: GraphicsPipelineDescription,
) : GpuPipeline {
    private var closed = false

    override val isClosed: Boolean
        get() = closed

    override fun close() {
        closed = true
    }
}
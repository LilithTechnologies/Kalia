package re.lilith.kalia.shader

import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.ShaderProgram

internal class PipelineCacheData {
    val probe = PipelineKey()

    var epoch = -1

    var lastPipeline: GpuPipeline? = null
    var lastProgram: ShaderProgram? = null
    var lastVertexFormat: VertexFormat? = null
    var lastAttachments: AttachmentLayout? = null
    var lastRaster: RasterState? = null
    var lastDepth: DepthState? = null
    var lastBlend: BlendState? = null
    var lastColorMask: ColorMask? = null

    fun forget() {
        lastPipeline = null
        lastProgram = null
        lastVertexFormat = null
        lastAttachments = null
        lastRaster = null
        lastDepth = null
        lastBlend = null
        lastColorMask = null
    }
}

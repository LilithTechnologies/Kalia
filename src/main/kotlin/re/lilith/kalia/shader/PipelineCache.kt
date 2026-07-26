package re.lilith.kalia.shader

import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.ShaderProgram

object PipelineCache {
    private var lastPipeline: GpuPipeline? = null
    private var lastProgram: ShaderProgram? = null
    private var lastVertexFormat: VertexFormat? = null
    private var lastAttachments: AttachmentLayout? = null
    private var lastRaster: RasterState? = null
    private var lastDepth: DepthState? = null
    private var lastBlend: BlendState? = null
    private var lastColorMask: ColorMask? = null

    var missCount: Long = 0L
        private set

    fun pipelineFor(
        device: RenderDevice,
        program: ShaderProgram,
        vertexFormat: VertexFormat?,
        attachments: AttachmentLayout,
    ): GpuPipeline {
        val raster = GlState.rasterState()
        val depth = GlState.depthState()
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val memo = lastPipeline
        if (memo != null &&
            lastProgram === program &&
            lastVertexFormat === vertexFormat &&
            lastAttachments == attachments &&
            lastRaster === raster &&
            lastDepth === depth &&
            lastBlend === blend &&
            lastColorMask === colorMask
        ) {
            return memo
        }

        val pipeline = device.createPipeline(
            GraphicsPipelineDescription(
                program = program,
                vertexFormat = vertexFormat,
                attachments = attachments,
                raster = raster,
                depth = if (attachments.depthFormat != null) depth else DepthState.DISABLED,
                blend = blend,
                colorMask = colorMask,
            ),
        )

        lastPipeline = pipeline
        lastProgram = program
        lastVertexFormat = vertexFormat
        lastAttachments = attachments
        lastRaster = raster
        lastDepth = depth
        lastBlend = blend
        lastColorMask = colorMask
        missCount++
        return pipeline
    }

    fun invalidate() {
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

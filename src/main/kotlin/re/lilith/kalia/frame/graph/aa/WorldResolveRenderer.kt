package re.lilith.kalia.frame.graph.aa

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.renderer.post.drawFullscreen
import re.lilith.kalia.renderer.resource.SamplerDescription
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WorldResolveRenderer {
    private val pushConstantScratch by lazy {
        ByteBuffer.allocateDirect(FxaaShaders.FAST_PROGRAM.pushConstantBytes).order(ByteOrder.nativeOrder())
    }
    private val paramFloats by lazy { (FxaaShaders.FAST_PROGRAM.pushConstantBytes - 16) / 4 }

    fun render(
        context: PassContext,
        input: TextureHandle,
        fxaaMode: FxaaMode,
        upscaleMode: UpscaleMode,
        upscaleSharpness: Float,
    ) {
        val (program, sampler) = when (fxaaMode) {
            FxaaMode.FAST -> FxaaShaders.FAST_PROGRAM to SamplerDescription.LINEAR_CLAMP
            FxaaMode.QUALITY -> FxaaShaders.QUALITY_PROGRAM to SamplerDescription.LINEAR_CLAMP
            FxaaMode.OFF -> when (upscaleMode) {
                UpscaleMode.NEAREST -> PostEffects.blit to SamplerDescription.NEAREST_CLAMP
                UpscaleMode.BILINEAR -> PostEffects.blit to SamplerDescription.LINEAR_CLAMP
                UpscaleMode.SHARP -> UpscaleShaders.SHARP_PROGRAM to SamplerDescription.LINEAR_CLAMP
            }
        }

        with(context) {
            val source = resolve(input)
            val gpuSampler = device.createSampler(sampler)
            val pipeline = device.createPipeline(
                GraphicsPipelineDescription(
                    program = program,
                    vertexFormat = null,
                    attachments = attachments,
                    raster = RasterState.TWO_SIDED,
                ),
            )

            bindPipeline(pipeline)
            bindTexture(0, source, gpuSampler)
            pushConstants(encodePushConstants(source.extent, upscaleSharpness))
            drawFullscreen()
        }
    }

    private fun PassContext.encodePushConstants(sourceExtent: Extent, sharpness: Float): ByteBuffer {
        val buffer = pushConstantScratch
        buffer.clear()
        buffer.putFloat(sharpness)
        repeat(paramFloats - 1) { buffer.putFloat(0f) }
        buffer.putFloat(1f / sourceExtent.width)
        buffer.putFloat(1f / sourceExtent.height)
        buffer.putFloat(extent.width.toFloat())
        buffer.putFloat(extent.height.toFloat())
        buffer.flip()
        return buffer
    }
}

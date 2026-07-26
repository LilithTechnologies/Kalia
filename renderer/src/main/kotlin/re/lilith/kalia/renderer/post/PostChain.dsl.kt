package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.LoadOp
import re.lilith.kalia.renderer.graph.RenderGraphBuilder
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.TextureSizing
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A screen-space effect chain.
 *
 * Example:
 * ```
 * postChain(scene, TextureHandle.BACKBUFFER) {
 *     stage("blur-h", PostEffects.gaussian) { params { vec2(1f, 0f); float(radius) } }
 *     stage("blur-v", PostEffects.gaussian) { params { vec2(0f, 1f); float(radius) } }
 *     stage("tonemap", PostEffects.aces)
 * }
 * ```
 */
fun RenderGraphBuilder.postChain(
    source: TextureHandle,
    target: TextureHandle,
    name: String = "post",
    build: PostChainBuilder.() -> Unit,
) {
    val stages = PostChainBuilder().apply(build).stages
    if (stages.isEmpty()) {
        // An empty chain still has to move the pixels, otherwise the target keeps stale contents
        addBlitPass("$name-passthrough", source, target)
        return
    }

    // One intermediate per non-final stage
    val intermediates = stages.dropLast(1).map { stage ->
        texture(
            name = "$name-${stage.name}",
            format = stage.format,
            sizing = TextureSizing.RelativeToBackbuffer(stage.scale),
        )
    }

    stages.forEachIndexed { index, stage ->
        val input = if (index == 0) source else intermediates[index - 1]
        val output = if (index == stages.lastIndex) target else intermediates[index]
        addStagePass("$name/${stage.name}", stage, input, output)
    }
}

private fun RenderGraphBuilder.addStagePass(
    passName: String,
    stage: PostStage,
    input: TextureHandle,
    output: TextureHandle,
) {
    pass(passName) {
        color(output, load = if (stage.blend.enabled) LoadOp.LOAD else LoadOp.DISCARD)
        reads(listOf(input) + stage.extraInputs)
        draw {
            val sampler = device.createSampler(stage.sampler)
            val pipeline = device.createPipeline(
                GraphicsPipelineDescription(
                    program = stage.program,
                    vertexFormat = null,
                    attachments = AttachmentLayout(listOf(resolve(output).format)),
                    raster = RasterState.TWO_SIDED,
                    depth = DepthState.DISABLED,
                    blend = stage.blend,
                ),
            )
            bindPipeline(pipeline)
            bindTexture(0, resolve(input), sampler)
            stage.extraInputs.forEachIndexed { index, handle ->
                bindTexture(index + 1, resolve(handle), sampler)
            }
            pushConstants(encodePushConstants(stage.params, resolve(input), extent))
            drawFullscreen()
        }
    }
}

private fun RenderGraphBuilder.addBlitPass(passName: String, input: TextureHandle, output: TextureHandle) {
    pass(passName) {
        color(output, load = LoadOp.DISCARD)
        reads(input)
        draw {
            val pipeline = device.createPipeline(
                GraphicsPipelineDescription(
                    program = PostEffects.blit,
                    vertexFormat = null,
                    attachments = AttachmentLayout(listOf(resolve(output).format)),
                    raster = RasterState.TWO_SIDED,
                ),
            )
            bindPipeline(pipeline)
            bindTexture(0, resolve(input), device.createSampler(SamplerDescription.LINEAR_CLAMP))
            pushConstants(encodePushConstants(FloatArray(PostStage.PARAM_FLOATS), resolve(input), extent))
            drawFullscreen()
        }
    }
}

/**
 * Draws the fullscreen triangle the post-processing vertex shader expects
 */
fun PassContext.drawFullscreen() = draw(vertexCount = 3)

private fun PassContext.encodePushConstants(
    params: FloatArray,
    input: GpuTexture,
    output: Extent,
): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(PostStage.PARAM_FLOATS * 4 + 16).order(ByteOrder.nativeOrder())
    params.forEach(buffer::putFloat)
    buffer.putFloat(1f / input.extent.width)
    buffer.putFloat(1f / input.extent.height)
    buffer.putFloat(output.width.toFloat())
    buffer.putFloat(output.height.toFloat())
    buffer.flip()
    return buffer
}
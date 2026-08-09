package re.lilith.kalia.renderer.command.list

import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import java.nio.ByteBuffer

/**
 * A [PassEncoder] that writes what it was asked to do as text
 */
class TextPassEncoder(
    override val extent: Extent = Extent(1, 1),
    override val attachments: AttachmentLayout = AttachmentLayout(emptyList(), null),
) : PassEncoder {
    val lines: List<String>
        field = mutableListOf<String>()

    private fun log(text: String) {
        lines += text
    }

    override fun viewport(viewport: Viewport) = log(
        "viewport ${viewport.x},${viewport.y} ${viewport.width}x${viewport.height} " +
                "depth=${viewport.minDepth}..${viewport.maxDepth}",
    )

    override fun scissor(rect: Rect?) =
        log(if (rect == null) "scissor none" else "scissor ${rect.x},${rect.y} ${rect.width}x${rect.height}")

    override fun bindPipeline(pipeline: GpuPipeline) = log("bindPipeline ${pipeline.label}")

    override fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler) =
        log("bindTexture $binding ${texture.label} ${sampler.label}")

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        log("bindUniformBuffer $binding ${buffer.label} +$offsetBytes size=$sizeBytes")

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        log("bindStorageBuffer $binding ${buffer.label} +$offsetBytes size=$sizeBytes")

    override fun pushConstants(data: ByteBuffer) = log("pushConstants ${data.remaining()} bytes")

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long) =
        log("bindVertexBuffer $slot ${buffer.label} +$offsetBytes")

    override fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long) =
        log("bindIndexBuffer ${buffer.label} $format +$offsetBytes")

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) =
        log("draw verts=$vertexCount inst=$instanceCount firstVertex=$firstVertex firstInstance=$firstInstance")

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) = log(
        "drawIndexed indices=$indexCount inst=$instanceCount firstIndex=$firstIndex " +
                "vertexOffset=$vertexOffset firstInstance=$firstInstance",
    )

    override fun drawIndexedIndirect(buffer: GpuBuffer, offsetBytes: Long, drawCount: Int, strideBytes: Int) =
        log("drawIndexedIndirect ${buffer.label} +$offsetBytes draws=$drawCount stride=$strideBytes")

    override fun multiDrawIndexed(draws: MultiDrawList) {
        val records = (0 until draws.size).joinToString(" ") { index ->
            "(${draws.indexCount(index)},${draws.firstIndex(index)},${draws.vertexOffset(index)})"
        }
        log("multiDrawIndexed ${draws.size} $records")
    }

    override fun depthBias(constant: Float, slope: Float) = log("depthBias $constant $slope")

    override fun lineWidth(width: Float) = log("lineWidth $width")

    override fun clearAttachments(color: Color?, depth: Float?, area: Rect?) {
        val colorText = color?.let { "${it.red},${it.green},${it.blue},${it.alpha}" } ?: "none"
        val areaText = area?.let { "${it.x},${it.y} ${it.width}x${it.height}" } ?: "full"
        log("clearAttachments color=$colorText depth=${depth ?: "none"} area=$areaText")
    }

    override fun retarget(color: GpuTexture?, depth: GpuTexture?) =
        log("retarget color=${color?.label ?: "default"} depth=${depth?.label ?: "none"}")
}

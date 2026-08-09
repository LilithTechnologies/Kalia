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
 * A [PassEncoder] that writes commands into a [CommandStream] rather than issuing them
 */
class CommandListRecorder(
    override val extent: Extent,
    override val attachments: AttachmentLayout,
    val stream: CommandStream = CommandStream(),
) : PassEncoder {

    override fun viewport(viewport: Viewport) {
        stream.command(Opcode.VIEWPORT)
        stream.int(viewport.x)
        stream.int(viewport.y)
        stream.int(viewport.width)
        stream.int(viewport.height)
        stream.float(viewport.minDepth)
        stream.float(viewport.maxDepth)
    }

    override fun scissor(rect: Rect?) {
        stream.command(Opcode.SCISSOR)
        stream.flag(rect != null)
        stream.int(rect?.x ?: 0)
        stream.int(rect?.y ?: 0)
        stream.int(rect?.width ?: 0)
        stream.int(rect?.height ?: 0)
    }

    override fun bindPipeline(pipeline: GpuPipeline) {
        stream.command(Opcode.BIND_PIPELINE)
        stream.int(stream.resources.idOf(pipeline))
    }

    override fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler) {
        stream.command(Opcode.BIND_TEXTURE)
        stream.int(binding)
        stream.int(stream.resources.idOf(texture))
        stream.int(stream.resources.idOf(sampler))
    }

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(Opcode.BIND_UNIFORM_BUFFER, binding, buffer, offsetBytes, sizeBytes)

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(Opcode.BIND_STORAGE_BUFFER, binding, buffer, offsetBytes, sizeBytes)

    private fun bindBuffer(opcode: Int, binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) {
        stream.command(opcode)
        stream.int(binding)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
        stream.long(sizeBytes)
    }

    override fun pushConstants(data: ByteBuffer) {
        stream.command(Opcode.PUSH_CONSTANTS)
        stream.blob(data)
    }

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long) {
        stream.command(Opcode.BIND_VERTEX_BUFFER)
        stream.int(slot)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
    }

    override fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long) {
        stream.command(Opcode.BIND_INDEX_BUFFER)
        stream.int(stream.resources.idOf(buffer))
        stream.int(format.ordinal)
        stream.long(offsetBytes)
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        stream.command(Opcode.DRAW)
        stream.int(vertexCount)
        stream.int(instanceCount)
        stream.int(firstVertex)
        stream.int(firstInstance)
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        stream.command(Opcode.DRAW_INDEXED)
        stream.int(indexCount)
        stream.int(instanceCount)
        stream.int(firstIndex)
        stream.int(vertexOffset)
        stream.int(firstInstance)
    }

    override fun drawIndexedIndirect(buffer: GpuBuffer, offsetBytes: Long, drawCount: Int, strideBytes: Int) {
        stream.command(Opcode.DRAW_INDEXED_INDIRECT)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
        stream.int(drawCount)
        stream.int(strideBytes)
    }

    override fun multiDrawIndexed(draws: MultiDrawList) {
        stream.command(Opcode.MULTI_DRAW_INDEXED)
        stream.int(draws.size)
        for (index in 0 until draws.size) {
            stream.int(draws.indexCount(index))
            stream.int(draws.firstIndex(index))
            stream.int(draws.vertexOffset(index))
        }
    }

    override fun depthBias(constant: Float, slope: Float) {
        stream.command(Opcode.DEPTH_BIAS)
        stream.float(constant)
        stream.float(slope)
    }

    override fun lineWidth(width: Float) {
        stream.command(Opcode.LINE_WIDTH)
        stream.float(width)
    }

    override fun clearAttachments(color: Color?, depth: Float?, area: Rect?) {
        stream.command(Opcode.CLEAR_ATTACHMENTS)
        stream.flag(color != null)
        stream.float(color?.red ?: 0f)
        stream.float(color?.green ?: 0f)
        stream.float(color?.blue ?: 0f)
        stream.float(color?.alpha ?: 0f)
        stream.flag(depth != null)
        stream.float(depth ?: 0f)
        stream.flag(area != null)
        stream.int(area?.x ?: 0)
        stream.int(area?.y ?: 0)
        stream.int(area?.width ?: 0)
        stream.int(area?.height ?: 0)
    }

    override fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        stream.command(Opcode.RETARGET)
        stream.flag(color != null)
        stream.int(color?.let { stream.resources.idOf(it) } ?: 0)
        stream.flag(depth != null)
        stream.int(depth?.let { stream.resources.idOf(it) } ?: 0)
    }
}

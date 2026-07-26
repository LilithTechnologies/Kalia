package re.lilith.kalia.renderer.command

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
 * Records draws inside a single render-graph pass
 */
interface PassEncoder {
    val extent: Extent

    val attachments: AttachmentLayout

    fun viewport(viewport: Viewport)
    fun scissor(rect: Rect?)

    fun bindPipeline(pipeline: GpuPipeline)
    fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler)
    fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)
    fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)

    fun pushConstants(data: ByteBuffer)

    fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long = 0L)
    fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long = 0L)

    fun draw(vertexCount: Int, instanceCount: Int = 1, firstVertex: Int = 0, firstInstance: Int = 0)
    fun drawIndexed(
        indexCount: Int,
        instanceCount: Int = 1,
        firstIndex: Int = 0,
        vertexOffset: Int = 0,
        firstInstance: Int = 0,
    )

    fun multiDrawIndexed(draws: MultiDrawList)

    fun depthBias(constant: Float, slope: Float)
    fun lineWidth(width: Float)

    fun clearAttachments(color: Color? = null, depth: Float? = null, area: Rect? = null)

    /**
     * Redirects everything recorded from here on into [color] and [depth]
     */
    fun retarget(color: GpuTexture?, depth: GpuTexture? = null)
}


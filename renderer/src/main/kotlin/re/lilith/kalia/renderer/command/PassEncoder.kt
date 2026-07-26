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
 * Encodes rendering commands for a single render pass.
 *
 * @author Lunasa
 * @since 1.0.0
 */
interface PassEncoder {
    /**
     * The dimensions of the current render target.
     */
    val extent: Extent

    /**
     * The attachment layout currently active for this pass.
     */
    val attachments: AttachmentLayout

    /**
     * Sets the viewport used for subsequent draw operations.
     *
     * @param viewport The viewport to apply.
     */
    fun viewport(viewport: Viewport)

    /**
     * Sets the active scissor rectangle.
     *
     * @param rect The scissor rectangle, or `null` to disable scissoring.
     */
    fun scissor(rect: Rect?)

    /**
     * Binds a graphics pipeline for subsequent draw calls.
     *
     * @param pipeline The pipeline to bind.
     */
    fun bindPipeline(pipeline: GpuPipeline)

    /**
     * Binds a sampled texture and sampler to a shader binding slot.
     *
     * @param binding The shader binding index.
     * @param texture The texture to bind.
     * @param sampler The sampler used when accessing the texture.
     */
    fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler)


    /**
     * Binds a uniform buffer range to a shader binding slot.
     *
     * @param binding The shader binding index.
     * @param buffer The buffer to bind.
     * @param offsetBytes Byte offset into the buffer.
     * @param sizeBytes Size of the bound range in bytes.
     */
    fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)

    /**
     * Binds a storage buffer range to a shader binding slot.
     *
     * @param binding The shader binding index.
     * @param buffer The buffer to bind.
     * @param offsetBytes Byte offset into the buffer.
     * @param sizeBytes Size of the bound range in bytes.
     */
    fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)

    /**
     * Uploads push constant data for subsequent shader invocations.
     *
     * @param data The push constant payload.
     */
    fun pushConstants(data: ByteBuffer)

    /**
     * Binds a vertex buffer to the specified input slot.
     *
     * @param slot The vertex buffer slot.
     * @param buffer The buffer to bind.
     * @param offsetBytes Byte offset into the buffer.
     */
    fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long = 0L)

    /**
     * Binds an index buffer for indexed draw operations.
     *
     * @param buffer The index buffer.
     * @param format The index element format.
     * @param offsetBytes Byte offset into the buffer.
     */
    fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long = 0L)

    /**
     * Issues a non-indexed draw.
     *
     * @param vertexCount Number of vertices to draw.
     * @param instanceCount Number of instances to draw.
     * @param firstVertex First vertex to read from.
     * @param firstInstance First instance identifier.
     */
    fun draw(vertexCount: Int, instanceCount: Int = 1, firstVertex: Int = 0, firstInstance: Int = 0)

    /**
     * Issues an indexed draw.
     *
     * @param indexCount Number of indices to draw.
     * @param instanceCount Number of instances to draw.
     * @param firstIndex First index to read from.
     * @param vertexOffset Value added to each fetched index.
     * @param firstInstance First instance identifier.
     */
    fun drawIndexed(
        indexCount: Int,
        instanceCount: Int = 1,
        firstIndex: Int = 0,
        vertexOffset: Int = 0,
        firstInstance: Int = 0,
    )

    /**
     * Executes multiple indexed draws from a prebuilt [MultiDrawList].
     *
     * The implementation may vary by backend. On Vulkan, the [VK_EXT_multi_draw](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_multi_draw.html) extension is
     * used where available. If it is not available, an indirect draw is used. If both are unavailable, the list is
     * iterated over on the CPU and indexed draws are used.
     *
     * @param draws The draw commands to execute.
     */
    fun multiDrawIndexed(draws: MultiDrawList)

    /**
     * Sets dynamic depth bias state.
     *
     * @param constant Constant depth bias factor.
     * @param slope Slope-scaled depth bias factor.
     */
    fun depthBias(constant: Float, slope: Float)

    /**
     * Sets the dynamic rasterization line width.
     *
     * @param width Line width in pixels.
     */
    fun lineWidth(width: Float)

    /**
     * Clears one or more attachments within the current render target.
     *
     * Any parameter left as `null` is not cleared.
     *
     * @param color Color value used when clearing color attachments.
     * @param depth Depth value used when clearing depth attachments.
     * @param area Optional region to clear. If omitted, the entire target is cleared.
     */
    fun clearAttachments(color: Color? = null, depth: Float? = null, area: Rect? = null)

    /**
     * Redirects subsequent rendering into different attachments.
     *
     * @param color The new color attachment, or `null`.
     * @param depth The new depth attachment, or `null`.
     */
    fun retarget(color: GpuTexture?, depth: GpuTexture? = null)
}


package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.GraphPass
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import java.nio.ByteBuffer
import kotlin.compareTo

internal class HeadlessPassContext(
    override val device: RenderDevice,
    private val graph: RenderGraph,
    private val textures: Map<TextureHandle, HeadlessTexture>,
    override val extent: Extent,
    override val attachments: AttachmentLayout,
) : PassContext {
    private var pipeline: HeadlessPipeline? = null

    private val boundTextures = HashMap<Int, HeadlessTexture>()
    private val boundSamplers = HashMap<Int, HeadlessSampler>()
    private val uniformBuffers = HashMap<Int, HeadlessBuffer>()

    private var issuedDraw = false

    private var currentViewport: Viewport? = null
    private var currentScissor: Rect? = null
    private val storageBuffers = HashMap<Int, HeadlessBuffer>()
    private val vertexBuffers = HashMap<Int, HeadlessBuffer>()
    private var indexBuffer: HeadlessBuffer? = null
    private var depthBiasConstant = 0f
    private var depthBiasSlope = 0f

    override fun viewport(viewport: Viewport) {
        require(viewport.width > 0f)
        require(viewport.height > 0f)

        currentViewport = viewport
    }

    override fun scissor(rect: Rect?) {
        rect?.let {
            require(it.width > 0)
            require(it.height > 0)
        }

        currentScissor = rect
    }

    override fun bindVertexBuffer(
        slot: Int,
        buffer: GpuBuffer,
        offsetBytes: Long,
    ) {
        val headless = buffer as? HeadlessBuffer
            ?: error("Vertex buffer must belong to headless backend.")

        require(slot >= 0)
        require(offsetBytes >= 0)
        require(offsetBytes <= headless.sizeBytes)

        vertexBuffers[slot] = headless
    }

    override fun bindIndexBuffer(
        buffer: GpuBuffer,
        format: IndexFormat,
        offsetBytes: Long,
    ) {
        val headless = buffer as? HeadlessBuffer
            ?: error("Index buffer must belong to headless backend.")

        require(offsetBytes >= 0)
        require(offsetBytes <= headless.sizeBytes)

        indexBuffer = headless
    }

    override fun multiDrawIndexed(draws: MultiDrawList) {
        requirePipeline()

        check(indexBuffer != null) {
            "Indexed draw requires an index buffer."
        }

        require(draws.size > 0) {
            "Multi-draw list is empty."
        }

        issuedDraw = true
    }

    override fun depthBias(constant: Float, slope: Float) {
        depthBiasConstant = constant
        depthBiasSlope = slope
    }

    override fun lineWidth(width: Float) {
        require(width > 0f) {
            "Line width must be greater than zero."
        }
    }

    override fun clearAttachments(
        color: Color?,
        depth: Float?,
        area: Rect?,
    ) {
        area?.let {
            require(it.width > 0)
            require(it.height > 0)
        }

        depth?.let {
            require(it in 0f..1f)
        }
    }

    override fun retarget(
        color: GpuTexture?,
        depth: GpuTexture?,
    ) {
        color?.let {
            require(it is HeadlessTexture) {
                "Color target must belong to headless backend."
            }
            check(!it.isClosed)
        }

        depth?.let {
            require(it is HeadlessTexture) {
                "Depth target must belong to headless backend."
            }
            check(!it.isClosed)
        }
    }

    override fun pushConstants(data: ByteBuffer) {
        val pipeline = requirePipeline()

        require(data.remaining() <= pipeline.description.program.pushConstantBytes) {
            "Pipeline '${pipeline.label}' allows ${pipeline.description.program.pushConstantBytes} bytes " +
                    "of push constants but ${data.remaining()} were provided."
        }
    }

    override fun bindStorageBuffer(
        binding: Int,
        buffer: GpuBuffer,
        offsetBytes: Long,
        sizeBytes: Long,
    ) {
        val headless = buffer as? HeadlessBuffer
            ?: error("Storage buffer must belong to headless backend.")

        require(binding >= 0)
        require(offsetBytes >= 0)
        require(sizeBytes >= 0)
        require(offsetBytes + sizeBytes <= headless.sizeBytes)

        storageBuffers[binding] = headless
    }

    fun beginPass(pass: GraphPass) {
        pipeline = null
        boundTextures.clear()
        boundSamplers.clear()
        uniformBuffers.clear()
    }

    fun endPass(pass: GraphPass) {
        require(pipeline != null || !issuedDraw) {
            "Pass '${pass.name}' recorded draw commands without binding a pipeline."
        }
    }

    override fun bindPipeline(pipeline: GpuPipeline) {
        val headless = pipeline as? HeadlessPipeline
            ?: error("Pipeline must belong to headless backend.")

        check(!headless.isClosed)

        this.pipeline = headless
    }

    override fun bindTexture(
        binding: Int,
        texture: GpuTexture,
        sampler: GpuSampler,
    ) {
        val headlessTexture = texture as? HeadlessTexture
            ?: error("Texture must belong to headless backend.")

        val headlessSampler = sampler as? HeadlessSampler
            ?: error("Sampler must belong to headless backend.")

        check(!headlessTexture.isClosed)
        check(!headlessSampler.isClosed)

        boundTextures[binding] = headlessTexture
        boundSamplers[binding] = headlessSampler
    }

    override fun bindUniformBuffer(
        binding: Int,
        buffer: GpuBuffer,
        offsetBytes: Long,
        sizeBytes: Long,
    ) {
        val headless = buffer as? HeadlessBuffer
            ?: error("Buffer must belong to headless backend.")

        require(offsetBytes >= 0)
        require(sizeBytes >= 0)
        require(offsetBytes + sizeBytes <= headless.sizeBytes)

        uniformBuffers[binding] = headless
    }

    override fun draw(
        vertexCount: Int,
        instanceCount: Int,
        firstVertex: Int,
        firstInstance: Int,
    ) {
        requirePipeline()

        require(vertexCount > 0)
        require(instanceCount > 0)
        require(firstVertex >= 0)
        require(firstInstance >= 0)

        issuedDraw = true
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        requirePipeline()

        require(indexCount > 0)
        require(instanceCount > 0)
        require(firstIndex >= 0)
        require(firstInstance >= 0)

        issuedDraw = true
    }

    private fun requirePipeline(): HeadlessPipeline =
        pipeline ?: error(
            "No pipeline is bound. Call bindPipeline() before drawing."
        )

    override fun resolve(handle: TextureHandle): GpuTexture =
        textures[handle]
            ?: error(
                "Texture handle ${handle.id} does not resolve to a texture in graph '${graph.name}'."
            )
}
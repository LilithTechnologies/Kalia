package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.*

/**
 * The one object a backend must provide
 */
interface RenderDevice : AutoCloseable {
    val capabilities: DeviceCapabilities

    val surfaceExtent: Extent
    val surfaceFormat: TextureFormat

    var settings: DeviceSettings

    fun createBuffer(description: BufferDescription): GpuBuffer
    fun createTexture(description: TextureDescription): GpuTexture
    fun createSampler(description: SamplerDescription): GpuSampler
    fun createPipeline(description: GraphicsPipelineDescription): GpuPipeline

    /**
     * Queues a device-side copy between buffers
     */
    fun copyBuffer(
        source: GpuBuffer,
        destination: GpuBuffer,
        sourceOffset: Long,
        destinationOffset: Long,
        sizeBytes: Long,
    )

    /**
     * Records and submits [graph] for the current frame
     */
    fun render(graph: RenderGraph): Boolean

    fun resize(extent: Extent)
    fun waitIdle()
}


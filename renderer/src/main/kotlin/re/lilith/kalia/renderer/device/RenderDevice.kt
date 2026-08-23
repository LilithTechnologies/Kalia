package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.command.MultiDrawLayout

import re.lilith.kalia.renderer.command.ComputeEncoder
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.pipeline.ComputePipelineDescription
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.*

/**
 * Primary interface to a rendering backend.
 *
 * Resources created by a device are owned by that device and
 * must not be used with resources from another device.
 *
 * @author Lunasa
 * @since 1.0.0
 */
interface RenderDevice : AutoCloseable {
    /**
     * Capabilities and implementation limits reported by the backend.
     */
    val capabilities: DeviceCapabilities

    /**
     * The [MultiDrawLayout] this backend consumes without repacking.
     */
    val preferredMultiDrawLayout get() = MultiDrawLayout.SEQUENTIAL

    /**
     * Current dimensions of the presentation surface in pixels.
     */
    val surfaceExtent: Extent

    /**
     * Format used by the presentation surface.
     */
    val surfaceFormat: TextureFormat

    /**
     * Mutable runtime settings for the device.
     * Modifying this value will change the settings.
     */
    var settings: DeviceSettings

    /**
     * Renderer invited to draw over each frame before it is presented, or null.
     *
     * @see PresentHook
     */
    var presentHook: PresentHook?
        get() = null
        set(_) = throw UnsupportedOperationException("This backend cannot host an external renderer.")

    var hudBoundaryHook: HudBoundaryHook?
        get() = null
        set(_) = throw UnsupportedOperationException("This backend cannot host an external renderer.")

    /**
     * Creates a GPU buffer.
     *
     * @param description Buffer creation parameters.
     * @return The newly created buffer.
     */
    fun createBuffer(description: BufferDescription): GpuBuffer

    /**
     * Creates a GPU texture.
     *
     * @param description Texture creation parameters.
     * @return The newly created texture.
     */
    fun createTexture(description: TextureDescription): GpuTexture


    /**
     * Creates a GPU sampler.
     *
     * @param description Sampler creation parameters.
     * @return The newly created sampler.
     */
    fun createSampler(description: SamplerDescription): GpuSampler

    /**
     * Creates a graphics pipeline.
     *
     * @param description Pipeline creation parameters.
     * @return The newly created pipeline.
     */
    fun createPipeline(description: GraphicsPipelineDescription): GpuPipeline

    /**
     * Queues a device-side copy between buffers.
     *
     * The copy is performed entirely on the GPU and does not require data to
     * pass through CPU memory.
     *
     * @param source Source buffer.
     * @param destination Destination buffer.
     * @param sourceOffset Byte offset into the source buffer.
     * @param destinationOffset Byte offset into the destination buffer.
     * @param sizeBytes Number of bytes to copy.
     */
    /**
     * Creates a compute pipeline. Only valid when [DeviceCapabilities.supportsCompute] is set.
     */
    fun createComputePipeline(description: ComputePipelineDescription): GpuComputePipeline =
        throw UnsupportedOperationException("This backend does not support compute.")

    /**
     * Records and submits compute work for this frame.
     */
    fun compute(body: (ComputeEncoder) -> Unit) {
        throw UnsupportedOperationException("This backend does not support compute.")
    }

    /**
     * The frame slot the next [render] will record into, in `0 until [DeviceCapabilities.framesInFlight]`.
     */
    val frameSlot: Int get() = 0

    /**
     * Waits until the GPU is finished with the resources belonging to [frameSlot], and
     * recycles them.
     */
    fun beginFrame() {
    }

    /**
     * Submits any staged uploads that are still pending, without waiting for them.
     */
    fun flushUploads() {
    }

    fun copyBuffer(
        source: GpuBuffer,
        destination: GpuBuffer,
        sourceOffset: Long,
        destinationOffset: Long,
        sizeBytes: Long,
    )

    /**
     * Records and submits a render graph for the current frame.
     *
     * @param graph The render graph to execute.
     * @return `true` if the frame was rendered successfully, otherwise
     * `false` if rendering should be skipped or retried.
     */
    fun render(graph: RenderGraph): Boolean

    fun render(graph: RenderGraph, slot: Int): Boolean = render(graph)

    fun endFrame() {}

    fun textureIndex(texture: GpuTexture, sampler: GpuSampler): Int = -1

    /**
     * Copies the most recently presented frame back into host memory.
     *
     * @return The captured frame, or `null` if nothing has been presented yet or
     * the backend cannot read back.
     */
    fun readFrame(): CapturedFrame? = null

    /**
     * Notifies the device that the presentation surface has changed size.
     *
     * @param extent The new surface dimensions.
     */
    fun resize(extent: Extent)

    /**
     * Number of occlusion queries the backend can track, or zero when it has none.
     */
    val occlusionQueryCapacity: Int get() = 0

    /**
     * Samples passed for [index] as of the most recent frame whose results were ready, or a
     * negative value when nothing is known yet.
     */
    fun occlusionResult(index: Int): Long = -1L

    /**
     * Declares how many queries the next frame will issue, so the backend can reset them.
     */
    fun prepareOcclusionQueries(count: Int) {}

    /**
     * Blocks until all previously submitted GPU work has completed.
     */
    fun waitIdle()

    /**
     * Releases all resources owned by this device.
     *
     * Implementations should ensure outstanding GPU work is completed or
     * safely discarded before destruction.
     */
    override fun close()
}


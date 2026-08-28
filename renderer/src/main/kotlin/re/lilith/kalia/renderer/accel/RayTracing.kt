package re.lilith.kalia.renderer.accel

import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuResource

/**
 * A built acceleration structure.
 *
 * Structures are owned by whoever created them and are released with [close];
 * the device does not keep them alive on the caller's behalf, because a world's
 * worth of chunk geometry produces far too many to pin to device teardown.
 */
interface GpuAccelerationStructure : GpuResource {
    val sizeBytes: Long
}

/**
 * Indexed triangles an acceleration structure is built over.
 *
 * The geometry is read in place out of buffers the renderer already owns, so no
 * copy of the world's geometry is kept. Positions are read as three tightly
 * packed floats at [vertexOffsetBytes] within each [vertexStrideBytes] vertex,
 * which lets an interleaved mesh format be traced without repacking.
 *
 * Both buffers must have been created with [re.lilith.kalia.renderer.resource.BufferDescription.rayTracingInput].
 */
data class TriangleGeometry(
    val vertexBuffer: GpuBuffer,
    val vertexOffsetBytes: Long,
    val vertexStrideBytes: Long,
    val vertexCount: Int,
    val indexBuffer: GpuBuffer,
    val indexOffsetBytes: Long,
    val indexCount: Int,
    /**
     * Whether the geometry can be assumed fully opaque. Cutout foliage still
     * counts as opaque here: alpha is resolved by the shader that reads the hit,
     * not by the traversal.
     */
    val opaque: Boolean = true,
) {
    init {
        require(vertexStrideBytes > 0L) { "Vertex stride must be positive." }
        require(vertexCount > 0) { "Vertex count must be positive." }
        require(indexCount > 0 && indexCount % 3 == 0) {
            "Index count must be a positive multiple of three, got $indexCount."
        }
    }
}

/**
 * Fills a top-level structure's instance list without allocating per instance.
 */
interface InstanceWriter {
    /**
     * Adds one instance of [structure] translated by [x], [y] and [z].
     *
     * @param customIndex Value the shader reads back to identify what was hit.
     * Limited to 24 bits.
     * @param mask ANDed against a ray's cull mask, so categories of geometry can
     * be skipped per ray.
     * @param twoSided Disables back-face culling, which block models rely on for
     * foliage, glass and water.
     */
    fun add(
        structure: GpuAccelerationStructure,
        x: Float,
        y: Float,
        z: Float,
        customIndex: Int,
        mask: Int = 0xFF,
        twoSided: Boolean = true,
    )
}

/**
 * The structure rays are actually traced against, holding one instance per piece
 * of bottom-level geometry.
 */
interface GpuTopLevelStructure : GpuAccelerationStructure {
    /**
     * The largest number of instances this structure was sized for.
     */
    val capacity: Int

    /**
     * Instances written by the most recent [update].
     */
    val instanceCount: Int

    /**
     * Rewrites the instance list and marks the structure for rebuild on the next
     * [RayTracingSupport.flushBuilds].
     *
     * Instances beyond [capacity] are dropped rather than throwing, so a sudden
     * spike in loaded geometry degrades instead of crashing the frame.
     */
    fun update(write: (InstanceWriter) -> Unit)
}

/**
 * Hardware ray tracing, exposed only when the device can actually do it.
 *
 * @see re.lilith.kalia.renderer.device.RenderDevice.rayTracing
 */
interface RayTracingSupport {
    /**
     * Creates a bottom-level structure over [geometry] and queues its build.
     *
     * The structure is not usable until the next [flushBuilds].
     */
    fun createBottomLevel(label: String, geometry: List<TriangleGeometry>): GpuAccelerationStructure

    /**
     * Creates a top-level structure sized for [maxInstances].
     */
    fun createTopLevel(label: String, maxInstances: Int): GpuTopLevelStructure

    /**
     * Records and submits every build queued since the last call, ordered so that
     * bottom-level builds complete before the top-level structures referencing
     * them, and so that later rendering sees the results.
     *
     * @return the number of structures built.
     */
    fun flushBuilds(): Int

    /**
     * Total device memory currently held by acceleration structures, in bytes.
     */
    val allocatedBytes: Long
}

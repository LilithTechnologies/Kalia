package re.lilith.kalia.frame.graph.rt

import dev.rdh.argentum.impl.render.terrain.CeleritasWorldRenderer
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager
import re.lilith.kalia.renderer.accel.GpuAccelerationStructure
import re.lilith.kalia.renderer.accel.GpuTopLevelStructure
import re.lilith.kalia.renderer.accel.TriangleGeometry
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.rendering.world.WorldFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor

/**
 * Keeps a traceable copy of the world in sync with the terrain the raster path
 * is already drawing.
 *
 * One bottom-level structure is built per section mesh, straight out of the
 * arena the chunk renderer uploaded it to, so no geometry is duplicated on the
 * host or the device. A top-level structure gathers the sections near the camera
 * into the scene rays are actually traced against.
 *
 * Everything here runs on the render thread, between the device handing out a
 * frame slot and the render graph being submitted.
 */
object RayTracingScene {

    /**
     * Sections are placed relative to a snapped origin rather than the exact
     * camera position, so the top-level structure only has to be rebuilt when the
     * camera crosses a section boundary instead of on every frame.
     */
    private const val ORIGIN_SNAP = 16.0

    /** Bytes per record in the instance description buffer. Matches the shader's std430 layout. */
    private const val INSTANCE_STRIDE = 16

    private const val MAX_INSTANCES = 16384

    /**
     * Bytes per emitter: position relative to the scene origin, then radiance.
     * Matches the shader's std430 layout.
     */
    private const val LIGHT_STRIDE = 32

    /**
     * Emitters the scene will hold at once. A torch-lit build easily has thousands
     * in view, and sampling picks from them rather than iterating them, so the cap
     * only bounds memory.
     */
    private const val MAX_LIGHTS = 8192

    private class Section(
        val blas: GpuAccelerationStructure,
        val vertexBuffer: GpuBuffer,
        val vertexOffsetElements: Int,
        val vertexCount: Int,
        val originX: Int,
        val originY: Int,
        val originZ: Int,
        val cutout: Boolean,
        val vertexAddress: Long,
        val vertexStride: Int,
    )

    private val sections = HashMap<Long, Section>()

    private var device: RenderDevice? = null
    private var indices: RayTracingQuadIndices? = null

    private var topLevel: Array<GpuTopLevelStructure?> = emptyArray()
    private var instanceBuffers: Array<GpuBuffer?> = emptyArray()
    private var sceneDirty = BooleanArray(0)

    private var knownRevision = Int.MIN_VALUE
    private var pendingRebuild = false

    private var originX = 0.0
    private var originY = 0.0
    private var originZ = 0.0

    /** Sections currently in the traceable set. */
    var instanceCount = 0
        private set

    /** Sections whose structures still need to be built. */
    var pendingSections = 0
        private set

    var structureBytes = 0L
        private set

    /**
     * Offset from the space Kalia renders the world in, which is relative to the
     * camera, to the space the top-level structure was built in. Rays have to be
     * shifted by this before they are traced.
     */
    val sceneOffsetX: Float get() = (cameraX - originX).toFloat()
    val sceneOffsetY: Float get() = (cameraY - originY).toFloat()
    val sceneOffsetZ: Float get() = (cameraZ - originZ).toFloat()

    private var cameraX = 0.0
    private var cameraY = 0.0
    private var cameraZ = 0.0

    /**
     * The structure to trace for the frame being recorded, or `null` when there
     * is nothing traceable yet.
     */
    fun structure(slot: Int): GpuAccelerationStructure? =
        topLevel.getOrNull(slot)?.takeIf { instanceCount > 0 }

    fun instanceBuffer(slot: Int): GpuBuffer? = instanceBuffers.getOrNull(slot)

    /**
     * Brings the traceable scene up to date with the terrain and the camera.
     *
     * @return true when there is something to trace.
     */
    fun update(device: RenderDevice): Boolean {
        val rayTracing = device.rayTracing ?: return false
        if (!RayTracingSettings.enabled) {
            return false
        }

        attach(device)

        val state = WorldFrame.consumedState
        if (!state.active) {
            return instanceCount > 0
        }

        cameraX = state.cameraX
        cameraY = state.cameraY
        cameraZ = state.cameraZ

        // Only re-snap once the camera has drifted a whole section away, so the
        // instance transforms stay valid across ordinary movement.
        if (abs(cameraX - originX) > ORIGIN_SNAP ||
            abs(cameraY - originY) > ORIGIN_SNAP ||
            abs(cameraZ - originZ) > ORIGIN_SNAP
        ) {
            originX = floor(cameraX / ORIGIN_SNAP) * ORIGIN_SNAP
            originY = floor(cameraY / ORIGIN_SNAP) * ORIGIN_SNAP
            originZ = floor(cameraZ / ORIGIN_SNAP) * ORIGIN_SNAP
            // Which sections fall inside the traced radius moves with the camera,
            // so the walk has to run again even though no geometry changed.
            pendingRebuild = true
            markSceneDirty()
        }

        val revision = RenderRegionManager.getGeometryRevision()
        if (revision != knownRevision || pendingRebuild) {
            knownRevision = revision
            pendingRebuild = reconcile(device, rayTracing)
            markSceneDirty()
        }

        val slot = device.frameSlot
        if (sceneDirty.getOrElse(slot) { false }) {
            writeInstances(slot)
            sceneDirty[slot] = false
        }

        rayTracing.flushBuilds()
        structureBytes = rayTracing.allocatedBytes
        return instanceCount > 0
    }

    private fun attach(device: RenderDevice) {
        if (this.device === device) {
            return
        }
        release()
        this.device = device

        val rayTracing = device.rayTracing ?: return
        val slots = device.capabilities.framesInFlight.coerceAtLeast(1)
        indices = RayTracingQuadIndices(device)
        topLevel = Array(slots) { slot -> rayTracing.createTopLevel("kalia-rt-scene-$slot", MAX_INSTANCES) }
        instanceBuffers = Array(slots) { slot ->
            device.createBuffer(
                BufferDescription(
                    label = "kalia-rt-instances-$slot",
                    sizeBytes = MAX_INSTANCES.toLong() * INSTANCE_STRIDE,
                    // Streaming keeps it host mapped so the list can be rewritten
                    // in place, while the ray tracing flag is what actually grants
                    // it the storage usage the trace shader reads it through.
                    usage = BufferUsage.STREAM,
                    transfer = true,
                    rayTracingInput = true,
                ),
            )
        }
        sceneDirty = BooleanArray(slots) { true }
    }

    private fun markSceneDirty() {
        // Every slot has to pick the change up, and each one only can while the
        // GPU is finished with it, which is exactly when its frame comes round.
        sceneDirty.fill(true)
    }

    /**
     * Walks the region storages and brings the per-section structures in line
     * with what is currently uploaded.
     *
     * @return true when work had to be deferred to a later frame.
     */
    private fun reconcile(device: RenderDevice, rayTracing: re.lilith.kalia.renderer.accel.RayTracingSupport): Boolean {
        val renderer = CeleritasWorldRenderer.instanceNullable() ?: return false
        val manager = renderer.renderSectionManager ?: return false
        val quadIndices = indices ?: return false

        val seen = HashSet<Long>(sections.size)
        var budget = RayTracingSettings.buildBudget
        var deferred = false
        val radius = RayTracingSettings.sceneRadius * 16

        for (region in manager.regions.loadedRegions) {
            for (pass in region.passes) {
                // Blended passes stay out of the traced scene and keep being drawn
                // by the raster path. Sortedness is the wrong test for this: it is
                // false whenever translucency sorting happens to be turned off,
                // which would quietly pull glass and water into the structure.
                if (pass.isBlended) {
                    continue
                }
                val storage = region.getStorage(pass) ?: continue
                val format = pass.vertexType().vertexFormat
                val resources = region.getResources(format) ?: continue
                val vertexBuffer = resources.vertexBuffer ?: continue
                val stride = format.stride

                // A build reads positions in place as three plain floats, so a
                // mesh format that packs them any other way cannot be traced.
                // The compact chunk format stores them as scaled unsigned shorts,
                // which would be read back as nonsense.
                if (!isTraceable(format)) {
                    continue
                }

                for (sectionIndex in 0 until RenderRegion.REGION_SIZE) {
                    val allocation = storage.getAllocation(sectionIndex) ?: continue
                    val vertexCount = allocation.length
                    if (vertexCount < 4) {
                        continue
                    }

                    val localX = (sectionIndex shr 5) and 7
                    val localY = sectionIndex and 3
                    val localZ = (sectionIndex shr 2) and 7
                    val worldX = region.originX + localX * 16
                    val worldY = region.originY + localY * 16
                    val worldZ = region.originZ + localZ * 16

                    if (abs(worldX + 8 - cameraX) > radius ||
                        abs(worldZ + 8 - cameraZ) > radius
                    ) {
                        continue
                    }

                    val key = sectionKey(worldX shr 4, worldY shr 4, worldZ shr 4, pass.supportsFragmentDiscard())

                    val existing = sections[key]
                    if (existing != null &&
                        existing.vertexBuffer === vertexBuffer &&
                        existing.vertexOffsetElements == allocation.offset &&
                        existing.vertexCount == vertexCount
                    ) {
                        // Unchanged, so its structure and vertex address both
                        // still describe what is uploaded.
                        seen += key
                        continue
                    }

                    if (budget <= 0) {
                        // The section moved within the arena and there is no
                        // budget left to rebuild it. Keeping the old entry would
                        // leave the shader dereferencing an address into a buffer
                        // the arena has already released, so it leaves the scene
                        // until a later frame can rebuild it properly.
                        deferred = true
                        continue
                    }

                    // Dropped before the structure is closed, so a failure below
                    // cannot leave a freed structure reachable.
                    sections.remove(key)?.blas?.close()

                    val quadCount = vertexCount / 4
                    quadIndices.ensure(quadCount)
                    val indexBuffer = quadIndices.buffer ?: continue

                    val offsetBytes = allocation.offset.toLong() * stride
                    val blas = rayTracing.createBottomLevel(
                        label = "kalia-rt-section",
                        geometry = listOf(
                            TriangleGeometry(
                                vertexBuffer = vertexBuffer,
                                vertexOffsetBytes = offsetBytes,
                                vertexStrideBytes = stride.toLong(),
                                vertexCount = vertexCount,
                                indexBuffer = indexBuffer,
                                indexOffsetBytes = 0L,
                                indexCount = quadCount * 6,
                            ),
                        ),
                    )

                    sections[key] = Section(
                        blas = blas,
                        vertexBuffer = vertexBuffer,
                        vertexOffsetElements = allocation.offset,
                        vertexCount = vertexCount,
                        originX = worldX,
                        originY = worldY,
                        originZ = worldZ,
                        cutout = pass.supportsFragmentDiscard(),
                        vertexAddress = device.bufferAddress(vertexBuffer) + offsetBytes,
                        vertexStride = stride,
                    )
                    seen += key
                    budget--
                }
            }
        }

        // Anything not seen either unloaded, left the traced radius, or moved
        // without the budget to follow it. All three mean it must not stay in the
        // scene, because its recorded vertex address may no longer be backed.
        val removed = sections.keys.filterNot(seen::contains)
        for (key in removed) {
            sections.remove(key)?.blas?.close()
        }

        pendingSections = removed.size
        return deferred
    }

    private fun writeInstances(slot: Int) {
        val structure = topLevel.getOrNull(slot) ?: return
        val buffer = instanceBuffers.getOrNull(slot) ?: return
        // Without a mapping there is no way to describe the scene, and quietly
        // tracing an empty one would look like the feature simply not working.
        val mapped = buffer.mapped()?.order(ByteOrder.nativeOrder())
            ?: error("The ray tracing instance buffer is not host mapped.")

        var index = 0
        structure.update { writer ->
            for (section in sections.values) {
                if (index >= MAX_INSTANCES) {
                    break
                }
                writeInstanceRecord(mapped, index, section)
                writer.add(
                    structure = section.blas,
                    x = (section.originX - originX).toFloat(),
                    y = (section.originY - originY).toFloat(),
                    z = (section.originZ - originZ).toFloat(),
                    customIndex = index,
                    // Cutout geometry is separated so a ray can choose to ignore
                    // foliage and glass without a second structure.
                    mask = if (section.cutout) MASK_CUTOUT else MASK_SOLID,
                    // Block models are only wound consistently for the faces the
                    // mesher kept, so back-face culling would open holes.
                    twoSided = true,
                )
                index++
            }
        }
        instanceCount = index
    }

    private fun writeInstanceRecord(target: ByteBuffer, index: Int, section: Section) {
        val base = index * INSTANCE_STRIDE
        target.putLong(base, section.vertexAddress)
        target.putInt(base + 8, section.vertexStride)
        target.putInt(base + 12, if (section.cutout) 1 else 0)
    }

    /**
     * Whether a chunk mesh format lays its positions out the way an acceleration
     * structure build needs: three consecutive 32-bit floats at the very start of
     * each vertex.
     */
    fun isTraceable(format: VertexFormat): Boolean {
        val position = format.attributes.minByOrNull { it.offset } ?: return false
        return position.offset == 0 && position.format == VertexAttributeFormat.FLOAT3
    }

    private fun sectionKey(x: Int, y: Int, z: Int, cutout: Boolean): Long {
        val packed = (x.toLong() and 0x3FFFFF shl 42) or
                (y.toLong() and 0xFFFFF shl 22) or
                (z.toLong() and 0x3FFFFF)
        return (packed shl 1) or (if (cutout) 1L else 0L)
    }

    /**
     * Drops every structure, which is what a world change or a device teardown
     * needs.
     */
    fun release() {
        sections.values.forEach { it.blas.close() }
        sections.clear()
        topLevel.forEach { it?.close() }
        topLevel = emptyArray()
        instanceBuffers.forEach { it?.close() }
        instanceBuffers = emptyArray()
        sceneDirty = BooleanArray(0)
        indices?.close()
        indices = null
        device = null
        instanceCount = 0
        pendingSections = 0
        structureBytes = 0L
        knownRevision = Int.MIN_VALUE
        pendingRebuild = false
    }

    const val MASK_SOLID = 0x01
    const val MASK_CUTOUT = 0x02
    const val MASK_ALL = MASK_SOLID or MASK_CUTOUT
}

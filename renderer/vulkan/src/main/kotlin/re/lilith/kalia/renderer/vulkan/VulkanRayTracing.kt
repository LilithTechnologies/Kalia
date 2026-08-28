package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.accel.GpuAccelerationStructure
import re.lilith.kalia.renderer.accel.GpuTopLevelStructure
import re.lilith.kalia.renderer.accel.InstanceWriter
import re.lilith.kalia.renderer.accel.RayTracingSupport
import re.lilith.kalia.renderer.accel.TriangleGeometry
import re.lilith.vulkan.api.accel.ACCELERATION_SCRATCH_ALIGNMENT
import re.lilith.vulkan.api.accel.AccelerationBuildInfo
import re.lilith.vulkan.api.accel.AccelerationGeometry
import re.lilith.vulkan.api.accel.AccelerationInstance
import re.lilith.vulkan.api.accel.AccelerationStructureType
import re.lilith.vulkan.api.accel.accelerationBuildSizes
import re.lilith.vulkan.api.accel.accelerationStructureBarrier
import re.lilith.vulkan.api.accel.buildAccelerationStructure
import re.lilith.vulkan.api.accel.createAccelerationStructure
import re.lilith.vulkan.api.debug.DebugNames
import re.lilith.vulkan.api.device.QueueSubmission
import re.lilith.vulkan.api.device.submit
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.memory.BufferConfig
import re.lilith.vulkan.api.memory.MemoryUsage
import re.lilith.vulkan.api.types.flags.BufferUsage
import java.nio.ByteOrder
import re.lilith.vulkan.api.accel.AccelerationStructure as VkAccelerationStructure

internal open class VulkanAccelerationStructure(
    private val owner: VulkanRayTracingSupport,
    override val label: String,
    val structure: VkAccelerationStructure,
) : GpuAccelerationStructure {
    private var closed = false

    override val sizeBytes: Long get() = structure.sizeBytes

    override val isClosed: Boolean get() = closed

    override fun close() {
        if (closed) return
        closed = true
        owner.release(this)
    }
}

internal class VulkanTopLevelStructure(
    private val owner: VulkanRayTracingSupport,
    label: String,
    structure: VkAccelerationStructure,
    override val capacity: Int,
    val instanceBuffer: Buffer,
) : VulkanAccelerationStructure(owner, label, structure), GpuTopLevelStructure {

    override var instanceCount: Int = 0
        private set

    private val writer = Writer()

    override fun update(write: (InstanceWriter) -> Unit) {
        writer.reset()
        write(writer)
        instanceCount = writer.count
        owner.queueTopLevel(this)
    }

    private inner class Writer : InstanceWriter {
        var count: Int = 0
        private val transform = FloatArray(12)
        private val view = instanceBuffer
            .mappedByteBuffer()
            .order(ByteOrder.nativeOrder())

        fun reset() {
            count = 0
        }

        override fun add(
            structure: GpuAccelerationStructure,
            x: Float,
            y: Float,
            z: Float,
            customIndex: Int,
            mask: Int,
            twoSided: Boolean,
        ) {
            // Overflowing is dropped rather than thrown: a sudden spike in loaded
            // geometry should cost distant detail, not the frame.
            if (count >= capacity) {
                return
            }
            val vulkan = structure as VulkanAccelerationStructure
            // Opacity is left to the geometry's own flag, so alpha-tested
            // sections can still surface candidates for the shader to reject.
            var flags = 0
            if (twoSided) {
                flags = flags or AccelerationInstance.FLAG_TWO_SIDED
            }
            AccelerationInstance.write(
                target = view,
                offset = count * AccelerationInstance.STRIDE,
                transform = AccelerationInstance.translation(x, y, z, transform),
                customIndex = customIndex,
                mask = mask,
                flags = flags,
                structureAddress = vulkan.structure.deviceAddress,
            )
            count++
        }
    }
}

/**
 * Builds and owns the device-side scene rays are traced against.
 *
 * Builds are queued as structures are created or updated and are recorded
 * together on [flushBuilds], which lets an arbitrary number of chunk rebuilds
 * share one scratch allocation and one submission.
 */
internal class VulkanRayTracingSupport(
    private val backend: VulkanRenderDevice,
    private val context: VulkanContext,
) : AutoCloseable, RayTracingSupport {

    private class PendingBuild(
        val target: VulkanAccelerationStructure,
        val info: AccelerationBuildInfo,
        val scratchBytes: Long,
    )

    private val pendingBottom = ArrayList<PendingBuild>()
    private val pendingTop = ArrayList<PendingBuild>()
    private val live = HashSet<VulkanAccelerationStructure>()

    private var scratch: Buffer? = null
    private var scratchCapacity = 0L

    override var allocatedBytes: Long = 0L
        private set

    override fun createBottomLevel(label: String, geometry: List<TriangleGeometry>): GpuAccelerationStructure {
        require(geometry.isNotEmpty()) { "A bottom-level structure needs at least one geometry." }

        val info = AccelerationBuildInfo(
            type = AccelerationStructureType.BottomLevel,
            geometries = geometry.map { triangles ->
                AccelerationGeometry.Triangles(
                    vertexBuffer = (triangles.vertexBuffer as VulkanBuffer).buffer,
                    vertexOffset = triangles.vertexOffsetBytes,
                    vertexStride = triangles.vertexStrideBytes,
                    vertexCount = triangles.vertexCount,
                    indexBuffer = (triangles.indexBuffer as VulkanBuffer).buffer,
                    indexOffset = triangles.indexOffsetBytes,
                    indexCount = triangles.indexCount,
                    opaque = triangles.opaque,
                )
            },
            // Chunk geometry is built once and traced for as long as the section
            // stays unchanged, so traversal speed is worth the build cost.
            preferFastTrace = true,
        )

        val sizes = context.device.accelerationBuildSizes(info)
        val structure = context.allocator.createAccelerationStructure(
            device = context.device,
            type = AccelerationStructureType.BottomLevel,
            sizeBytes = sizes.structureBytes,
        )
        DebugNames.set(context.device, structure.storage, label)

        val wrapper = VulkanAccelerationStructure(this, label, structure)
        live += wrapper
        allocatedBytes += sizes.structureBytes
        pendingBottom += PendingBuild(wrapper, info, sizes.buildScratchBytes)
        return wrapper
    }

    override fun createTopLevel(label: String, maxInstances: Int): GpuTopLevelStructure {
        require(maxInstances > 0) { "A top-level structure needs room for at least one instance." }

        val instanceBuffer = context.allocator.createBuffer(
            BufferConfig(
                size = maxInstances.toLong() * AccelerationInstance.STRIDE,
                usage = BufferUsage.AccelerationStructureBuildInput +
                        BufferUsage.ShaderDeviceAddress +
                        BufferUsage.TransferDestination,
            ),
            // The instance list is rewritten from scratch every frame, so it
            // lives in host-visible memory the build reads directly rather than
            // paying for a staging copy each time.
            MemoryUsage.Upload,
        )
        DebugNames.set(context.device, instanceBuffer, "$label-instances")

        val info = topLevelInfo(instanceBuffer, maxInstances)
        val sizes = context.device.accelerationBuildSizes(info)
        val structure = context.allocator.createAccelerationStructure(
            device = context.device,
            type = AccelerationStructureType.TopLevel,
            sizeBytes = sizes.structureBytes,
        )
        DebugNames.set(context.device, structure.storage, label)

        val wrapper = VulkanTopLevelStructure(this, label, structure, maxInstances, instanceBuffer)
        live += wrapper
        allocatedBytes += sizes.structureBytes
        return wrapper
    }

    private fun topLevelInfo(instanceBuffer: Buffer, count: Int) = AccelerationBuildInfo(
        type = AccelerationStructureType.TopLevel,
        geometries = listOf(AccelerationGeometry.Instances(instanceBuffer, 0L, count)),
        // The instance list changes every frame, so a cheap build beats a fast trace.
        preferFastTrace = false,
    )

    fun queueTopLevel(structure: VulkanTopLevelStructure) {
        // A top-level build over zero instances is legal and produces an empty
        // scene, which is what an unloaded world should trace against.
        val info = topLevelInfo(structure.instanceBuffer, structure.instanceCount)
        val sizes = context.device.accelerationBuildSizes(info)
        pendingTop.removeAll { it.target === structure }
        pendingTop += PendingBuild(structure, info, sizes.buildScratchBytes)
    }

    fun release(structure: VulkanAccelerationStructure) {
        pendingBottom.removeAll { it.target === structure }
        pendingTop.removeAll { it.target === structure }
        if (live.remove(structure)) {
            allocatedBytes -= structure.sizeBytes
        }
        // Builds referencing this structure may still be in flight, so the
        // destruction rides the device's deferred release queue.
        backend.scheduleRelease(structure.structure)
        if (structure is VulkanTopLevelStructure) {
            backend.scheduleRelease(structure.instanceBuffer)
        }
    }

    override fun flushBuilds(): Int {
        if (pendingBottom.isEmpty() && pendingTop.isEmpty()) {
            return 0
        }

        val largest = maxOf(
            pendingBottom.maxOfOrNull { it.scratchBytes } ?: 0L,
            pendingTop.maxOfOrNull { it.scratchBytes } ?: 0L,
        )
        val scratchBuffer = ensureScratch(largest)
        val scratchBase = alignUp(scratchBuffer.deviceAddress, ACCELERATION_SCRATCH_ALIGNMENT)
        // Aligning the base can eat into the tail of the allocation.
        val usable = scratchCapacity - (scratchBase - scratchBuffer.deviceAddress)

        val commandBuffer = context.commandPool.allocatePrimary()
        val recorder = commandBuffer.begin()
        var built = 0

        var cursor = 0L
        for (build in pendingBottom) {
            if (build.scratchBytes > usable) {
                // A single build the scratch cannot hold would run off the end of
                // the allocation, which the driver has no way to detect.
                continue
            }
            if (cursor > 0L && cursor + build.scratchBytes > usable) {
                // Out of room, so the next batch has to wait for these to finish
                // writing before it can reuse the scratch region.
                recorder.accelerationStructureBarrier()
                cursor = 0L
            }
            recorder.buildAccelerationStructure(build.target.structure, build.info, scratchBase + cursor)
            cursor = alignUp(cursor + build.scratchBytes, ACCELERATION_SCRATCH_ALIGNMENT)
            built++
        }

        if (pendingBottom.isNotEmpty()) {
            // Instances cannot be traversed until the geometry they point at exists.
            recorder.accelerationStructureBarrier()
            cursor = 0L
        }

        for (build in pendingTop) {
            if (build.scratchBytes > usable) {
                continue
            }
            if (cursor > 0L && cursor + build.scratchBytes > usable) {
                recorder.accelerationStructureBarrier()
                cursor = 0L
            }
            recorder.buildAccelerationStructure(build.target.structure, build.info, scratchBase + cursor)
            cursor = alignUp(cursor + build.scratchBytes, ACCELERATION_SCRATCH_ALIGNMENT)
            built++
        }

        // The second scope of a barrier reaches commands submitted later on the
        // same queue, which is what makes the render graph's traces see this.
        recorder.accelerationStructureBarrier(traceable = true)
        val finished = recorder.end()

        // Builds are recorded on the render thread while presentation runs on the
        // game thread. A queue may only be touched by one thread at a time, and
        // racing a submit against a present is what a driver reports back as a
        // lost device.
        context.withQueueLock {
            context.graphicsQueue.submit(listOf(QueueSubmission(commandBuffers = listOf(finished))))
        }
        backend.scheduleRelease(commandBuffer)

        pendingBottom.clear()
        pendingTop.clear()
        return built
    }

    private fun ensureScratch(required: Long): Buffer {
        val existing = scratch
        // One slot of headroom past the largest single build keeps small batches
        // from reallocating every time a big chunk shows up.
        val target = maxOf(required * 2L, MINIMUM_SCRATCH_BYTES)
        if (existing != null && scratchCapacity >= required + ACCELERATION_SCRATCH_ALIGNMENT) {
            return existing
        }

        existing?.let(backend::scheduleRelease)
        val created = context.allocator.createBuffer(
            BufferConfig(
                size = target + ACCELERATION_SCRATCH_ALIGNMENT,
                usage = BufferUsage.StorageBuffer + BufferUsage.ShaderDeviceAddress,
            ),
            MemoryUsage.GpuOnly,
        )
        DebugNames.set(context.device, created, "kalia-rt-scratch")
        scratch = created
        scratchCapacity = target
        return created
    }

    override fun close() {
        pendingBottom.clear()
        pendingTop.clear()
        live.toList().forEach { it.close() }
        live.clear()
        scratch?.close()
        scratch = null
        scratchCapacity = 0L
        allocatedBytes = 0L
    }

    private companion object {
        const val MINIMUM_SCRATCH_BYTES = 8L * 1024 * 1024

        fun alignUp(value: Long, alignment: Long): Long = (value + alignment - 1L) / alignment * alignment
    }
}

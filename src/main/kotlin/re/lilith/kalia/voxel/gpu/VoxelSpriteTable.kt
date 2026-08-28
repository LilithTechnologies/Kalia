package re.lilith.kalia.voxel.gpu

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.voxel.VoxelFormat
import re.lilith.kalia.voxel.build.VoxelSprites
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors the interned atlas rectangles into a storage buffer the tracer indexes by sprite id.
 *
 * The table only grows, and only when a block type is voxelised for the first time, so in practice
 * it settles within the first few seconds of a world and is never touched again.
 */
class VoxelSpriteTable : AutoCloseable {
    private var device: RenderDevice? = null
    private var buffer: GpuBuffer? = null
    private var uploadedVersion = -1

    private val scratch = FloatArray(VoxelFormat.MAX_SPRITES * 4)
    private val staging: ByteBuffer = ByteBuffer
        .allocateDirect(VoxelFormat.MAX_SPRITES * 4 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    fun sync(device: RenderDevice): GpuBuffer? {
        val existing = buffer
        if (existing == null || this.device !== device) {
            existing?.close()
            this.device = device
            uploadedVersion = -1
            buffer = device.createBuffer(
                BufferDescription(
                    label = "kalia/svo-sprites",
                    // Sized for the full table up front: at 64 KiB it is not worth growing.
                    sizeBytes = VoxelFormat.MAX_SPRITES.toLong() * 4 * Float.SIZE_BYTES,
                    usage = BufferUsage.STORAGE,
                ),
            )
        }

        val target = buffer ?: return null
        if (VoxelSprites.version == uploadedVersion) {
            return target
        }

        uploadedVersion = VoxelSprites.snapshot(scratch)
        val floats = VoxelSprites.count.coerceAtMost(VoxelFormat.MAX_SPRITES) * 4
        if (floats == 0) {
            return target
        }
        staging.clear()
        staging.limit(floats * Float.SIZE_BYTES)
        staging.asFloatBuffer().put(scratch, 0, floats)
        target.write(staging, 0L)
        return target
    }

    override fun close() {
        buffer?.close()
        buffer = null
        device = null
        uploadedVersion = -1
    }
}

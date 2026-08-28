package re.lilith.vulkan.api.accel

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writer for `VkAccelerationStructureInstanceKHR` records.
 *
 * The layout is fixed by the specification and is not expressible as a Kotlin
 * data class without a copy per instance, so instances are written straight into
 * a mapped buffer instead.
 */
object AccelerationInstance {
    /** Byte size of one `VkAccelerationStructureInstanceKHR`. */
    const val STRIDE: Int = 64

    /** Skips any-hit invocations for the instance. */
    const val FLAG_OPAQUE: Int = 0x4

    /** Disables back-face culling, which block models rely on for foliage and glass. */
    const val FLAG_TWO_SIDED: Int = 0x1

    /**
     * Writes one instance at [offset] within [target].
     *
     * The transform is a row-major 3x4 affine matrix, laid out as three rows of
     * four floats, matching `VkTransformMatrixKHR`.
     *
     * @param customIndex Value the shader reads back as `gl_InstanceCustomIndexEXT`.
     * Only the low 24 bits are kept.
     * @param mask Visibility mask ANDed against the ray's `cullMask`.
     * @param structureAddress Device address of the bottom-level structure.
     */
    fun write(
        target: ByteBuffer,
        offset: Int,
        transform: FloatArray,
        customIndex: Int,
        mask: Int,
        flags: Int,
        structureAddress: Long,
    ) {
        require(transform.size >= 12) { "An instance transform is a row-major 3x4 matrix." }
        require(offset >= 0 && offset + STRIDE <= target.capacity()) {
            "Instance at $offset does not fit in a ${target.capacity()} byte buffer."
        }

        var cursor = offset
        for (index in 0 until 12) {
            target.putFloat(cursor, transform[index])
            cursor += 4
        }

        target.putInt(cursor, (customIndex and 0x00FFFFFF) or ((mask and 0xFF) shl 24))
        cursor += 4
        // The shader binding table record offset stays zero: ray queries do not
        // dispatch hit shaders, so there is no table to index into.
        target.putInt(cursor, (flags and 0xFF) shl 24)
        cursor += 4
        target.putLong(cursor, structureAddress)
    }

    /**
     * Writes one instance at the given index of a natively allocated array.
     */
    fun writeAt(address: Long, index: Int, transform: FloatArray, customIndex: Int, mask: Int, flags: Int, structureAddress: Long) {
        val buffer = MemoryUtil.memByteBuffer(address + index.toLong() * STRIDE, STRIDE).order(ByteOrder.nativeOrder())
        write(buffer, 0, transform, customIndex, mask, flags, structureAddress)
    }

    /**
     * An identity rotation with a translation, which is all block geometry needs:
     * chunk meshes are axis aligned and already baked in section-local space.
     */
    fun translation(x: Float, y: Float, z: Float, into: FloatArray = FloatArray(12)): FloatArray {
        into[0] = 1f; into[1] = 0f; into[2] = 0f; into[3] = x
        into[4] = 0f; into[5] = 1f; into[6] = 0f; into[7] = y
        into[8] = 0f; into[9] = 0f; into[10] = 1f; into[11] = z
        return into
    }
}

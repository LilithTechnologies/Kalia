package re.lilith.kalia.voxel.build

import re.lilith.kalia.voxel.VoxelFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interns block atlas rectangles so a voxel can name its texture in twelve bits.
 *
 * Sprites are discovered lazily, on whichever chunk build thread first voxelises a block that uses
 * one, and the table only ever grows. The shader reads it as a flat array of `vec4(u0, v0, u1, v1)`
 * and multiplies the face's local coordinate into that rectangle.
 */
object VoxelSprites {
    private val interned = ConcurrentHashMap<Long, Int>()
    private val counter = AtomicInteger()

    /** Four floats per sprite. Written once at intern time and never mutated afterwards. */
    private val rectangles = FloatArray(VoxelFormat.MAX_SPRITES * 4)

    /** Bumped whenever a sprite is added, so the GPU copy knows it is stale. */
    @Volatile
    var version: Int = 0
        private set

    val count: Int get() = counter.get()

    /**
     * @return the index for this atlas rectangle, or zero when the table is full.
     */
    fun intern(minU: Float, minV: Float, maxU: Float, maxV: Float): Int {
        val key = (java.lang.Float.floatToRawIntBits(minU).toLong() shl 32) or
            (java.lang.Float.floatToRawIntBits(minV).toLong() and 0xFFFFFFFFL)
        interned[key]?.let { return it }

        // Two threads racing on the same sprite would otherwise each claim an index. The loser's
        // slot is simply never referenced, which costs sixteen bytes and no correctness.
        val claimed = counter.getAndIncrement()
        if (claimed >= VoxelFormat.MAX_SPRITES) {
            counter.set(VoxelFormat.MAX_SPRITES)
            return 0
        }
        val existing = interned.putIfAbsent(key, claimed)
        if (existing != null) {
            return existing
        }

        val base = claimed * 4
        rectangles[base] = minU
        rectangles[base + 1] = minV
        rectangles[base + 2] = maxU
        rectangles[base + 3] = maxV
        version++
        return claimed
    }

    /**
     * Copies the table into [into], which must hold at least `count * 4` floats.
     *
     * @return the version the copy corresponds to.
     */
    fun snapshot(into: FloatArray): Int {
        val version = this.version
        val length = (counter.get().coerceAtMost(VoxelFormat.MAX_SPRITES)) * 4
        System.arraycopy(rectangles, 0, into, 0, length.coerceAtMost(into.size))
        return version
    }

    fun reset() {
        interned.clear()
        counter.set(0)
        version++
    }
}

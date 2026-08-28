package re.lilith.kalia.voxel.build

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import re.lilith.kalia.voxel.VoxelFormat

/**
 * Packs one chunk section into the brick layout the traversal reads.
 *
 * One of these lives on each chunk build worker and is reused for every section that thread
 * handles, so voxelising a section allocates nothing beyond the final word array. The output is a
 * bitmask pyramid, the palette of surfaces the section actually contains, and one narrow palette
 * index per solid voxel.
 */
class VoxelBrickBuilder {
    private val occupancy = IntArray(VoxelFormat.OCCUPANCY_WORDS)
    private val indices = IntArray(VoxelFormat.BRICK_VOXELS)
    private val light = ByteArray(VoxelFormat.BRICK_VOXELS)

    private var firstLight = -1
    private var uniformLight = true

    private val paletteLookup = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }
    private val paletteEntries = LongArray(VoxelFormat.MAX_PALETTE)
    private var paletteSize = 0

    private var solidCount = 0

    /** Number of solid voxels in the section. */
    val solid: Int get() = solidCount

    /** Distinct surfaces the section holds. */
    val palette: Int get() = paletteSize

    /**
     * Rough RGB565 average of the section, stored on the octree leaf for the level-of-detail path.
     * Derived from the palette tints rather than the textures, which is all the far field needs.
     */
    var averageColor565: Int = 0
        private set

    fun reset() {
        if (solidCount != 0) {
            java.util.Arrays.fill(occupancy, 0)
        }
        paletteLookup.clear()
        paletteSize = 0
        solidCount = 0
        firstLight = -1
        uniformLight = true
    }

    /**
     * Records one solid voxel. Coordinates are section-local, in `0 until 16`.
     *
     * @param entry the packed palette entry from [VoxelMaterials.of]
     * @param packedLight vanilla's sky and block levels, from [VoxelFormat.packLight]
     */
    fun put(x: Int, y: Int, z: Int, entry: Long, packedLight: Int) {
        val bit = VoxelFormat.voxelBit(x, y, z)
        val word = bit ushr 5
        val mask = 1 shl (bit and 31)
        if (occupancy[word] and mask == 0) {
            occupancy[word] = occupancy[word] or mask
            solidCount++
        }
        indices[bit] = paletteIndex(entry)
        light[bit] = packedLight.toByte()

        // Enclosed sections are uniformly dark, which is most of the world by volume, so noticing
        // that lets the light array collapse to a single byte.
        if (firstLight < 0) {
            firstLight = packedLight
        } else if (packedLight != firstLight) {
            uniformLight = false
        }
    }

    private fun paletteIndex(entry: Long): Int {
        val existing = paletteLookup.get(entry)
        if (existing >= 0) {
            return existing
        }
        if (paletteSize >= VoxelFormat.MAX_PALETTE) {
            // Vanishingly rare, and collapsing onto the last entry costs one mis-textured voxel
            // rather than a dropped section.
            return VoxelFormat.MAX_PALETTE - 1
        }
        val index = paletteSize++
        paletteEntries[index] = entry
        paletteLookup.put(entry, index)
        return index
    }

    /**
     * Emits the packed brick.
     *
     * @param out scratch at least [VoxelFormat.MAX_BRICK_WORDS] long; the caller keeps ownership.
     * @return the number of words written, or zero when the section held no solid voxels.
     */
    fun build(out: IntArray): Int {
        if (solidCount == 0) {
            return 0
        }

        var coarseLow = 0
        var coarseHigh = 0
        for (cellY in 0 until VoxelFormat.COARSE_EDGE) {
            for (cellZ in 0 until VoxelFormat.COARSE_EDGE) {
                for (cellX in 0 until VoxelFormat.COARSE_EDGE) {
                    if (!coarseOccupied(cellX, cellY, cellZ)) {
                        continue
                    }
                    val bit = VoxelFormat.coarseBit(cellX, cellY, cellZ)
                    if (bit < 32) {
                        coarseLow = coarseLow or (1 shl bit)
                    } else {
                        coarseHigh = coarseHigh or (1 shl (bit - 32))
                    }
                }
            }
        }
        out[VoxelFormat.COARSE_OFFSET] = coarseLow
        out[VoxelFormat.COARSE_OFFSET + 1] = coarseHigh

        System.arraycopy(occupancy, 0, out, VoxelFormat.OCCUPANCY_OFFSET, VoxelFormat.OCCUPANCY_WORDS)

        // Running population count at the start of every group of PREFIX_GROUP occupancy words,
        // stored two 16-bit entries per word. Turns a voxel hit into three bit counts at most.
        var running = 0
        var packed = 0
        for (group in 0 until VoxelFormat.OCCUPANCY_WORDS / VoxelFormat.PREFIX_GROUP) {
            if (group and 1 == 0) {
                packed = running and 0xFFFF
            } else {
                out[VoxelFormat.PREFIX_OFFSET + (group shr 1)] = packed or ((running and 0xFFFF) shl 16)
            }
            val first = group * VoxelFormat.PREFIX_GROUP
            for (word in first until first + VoxelFormat.PREFIX_GROUP) {
                running += Integer.bitCount(occupancy[word])
            }
        }

        val bits = VoxelFormat.bitsFor(paletteSize)
        val lightBits = if (uniformLight) 0 else 8
        out[VoxelFormat.PALETTE_HEADER_OFFSET] = VoxelFormat.paletteHeader(paletteSize, bits)
        out[VoxelFormat.LIGHT_INFO_OFFSET] = VoxelFormat.lightInfo(
            solidCount = solidCount,
            lightBits = lightBits,
            uniformLight = if (uniformLight) firstLight.coerceAtLeast(0) else 0,
        )

        var cursor = VoxelFormat.PALETTE_OFFSET
        var sumRed = 0
        var sumGreen = 0
        var sumBlue = 0
        for (index in 0 until paletteSize) {
            val entry = paletteEntries[index]
            val surface = VoxelMaterials.surfaceOf(entry)
            val tint = VoxelMaterials.tintOf(entry)
            out[cursor++] = surface
            out[cursor++] = tint
            val packed = VoxelFormat.tint444(tint)
            sumRed += (packed ushr 8) and 0xF
            sumGreen += (packed ushr 4) and 0xF
            sumBlue += packed and 0xF
        }
        averageColor565 = pack565(
            sumRed * 17 / paletteSize,
            sumGreen * 17 / paletteSize,
            sumBlue * 17 / paletteSize,
        )

        val indexWords = if (bits == 0) 0 else (solidCount * bits + 31) / 32
        val lightWords = if (lightBits == 0) 0 else (solidCount * 8 + 31) / 32
        val indexBase = cursor
        val lightBase = cursor + indexWords
        java.util.Arrays.fill(out, cursor, lightBase + lightWords, 0)

        if (bits == 0 && lightBits == 0) {
            return lightBase
        }

        // Both arrays are written in occupancy bit order, which is the order the shader recovers
        // from its running population count.
        var slot = 0
        val perWord = if (bits == 0) 1 else 32 / bits
        val mask = (1 shl bits) - 1
        for (word in 0 until VoxelFormat.OCCUPANCY_WORDS) {
            var remaining = occupancy[word]
            val base = word shl 5
            while (remaining != 0) {
                val bit = Integer.numberOfTrailingZeros(remaining)
                remaining = remaining and (remaining - 1)
                if (bits != 0) {
                    val value = indices[base or bit] and mask
                    out[indexBase + slot / perWord] = out[indexBase + slot / perWord] or
                        (value shl ((slot % perWord) * bits))
                }
                if (lightBits != 0) {
                    val value = light[base or bit].toInt() and 0xFF
                    out[lightBase + (slot shr 2)] = out[lightBase + (slot shr 2)] or
                        (value shl ((slot and 3) * 8))
                }
                slot++
            }
        }
        return lightBase + lightWords
    }

    private fun pack565(red: Int, green: Int, blue: Int): Int =
        (((red shr 3) and 0x1F) shl 11) or (((green shr 2) and 0x3F) shl 5) or ((blue shr 3) and 0x1F)

    private fun coarseOccupied(cellX: Int, cellY: Int, cellZ: Int): Boolean {
        val baseX = cellX * VoxelFormat.COARSE_SPAN
        val baseY = cellY * VoxelFormat.COARSE_SPAN
        val baseZ = cellZ * VoxelFormat.COARSE_SPAN
        for (y in baseY until baseY + VoxelFormat.COARSE_SPAN) {
            for (z in baseZ until baseZ + VoxelFormat.COARSE_SPAN) {
                // A row of four voxels along x never straddles a word boundary, because the bit
                // index is (y shl 8) or (z shl 4) or x and 4 divides 16.
                val bit = VoxelFormat.voxelBit(baseX, y, z)
                val row = (occupancy[bit ushr 5] ushr (bit and 31)) and 0xF
                if (row != 0) {
                    return true
                }
            }
        }
        return false
    }
}

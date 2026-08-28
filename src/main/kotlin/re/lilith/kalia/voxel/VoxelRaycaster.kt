package re.lilith.kalia.voxel

/**
 * CPU traversal of the octree, step for step the same algorithm `svo_common.glsl` runs.
 *
 * It exists for two reasons. Occlusion culling wants exact "can the camera see this box" answers
 * without a frame of readback latency, and having the traversal in Kotlin means it can be tested
 * against a brute-force reference — which is the only way to gain any confidence in the GLSL,
 * since the two implementations are the same algorithm written twice.
 *
 * Not thread safe. Each caller keeps its own instance, and callers must not run while
 * [VoxelWorld.tick] is mutating the arenas.
 */
class VoxelRaycaster {
    private val stack = IntArray(VoxelFormat.MAX_LEVELS + 2)

    /** Distance to the hit, in blocks. Only meaningful when the last cast returned true. */
    var hitDistance: Double = 0.0
        private set

    /** Material word of the voxel that was hit. */
    var hitMaterial: Int = 0
        private set

    /** Octree nodes visited by the last cast, which is the traversal cost of that ray. */
    var steps: Int = 0
        private set

    /**
     * Casts a ray against the octree.
     *
     * Coordinates are in blocks, relative to the octree's minimum corner, and [dirX]/[dirY]/[dirZ]
     * must be unit length so that distances come out in blocks.
     *
     * @return true when a solid voxel was hit within [maxDistance].
     */
    fun cast(
        originX: Double,
        originY: Double,
        originZ: Double,
        dirX: Double,
        dirY: Double,
        dirZ: Double,
        maxDistance: Double,
    ): Boolean = cast(
        nodes = VoxelWorld.nodes.storage.words,
        bricks = VoxelWorld.bricks.storage.words,
        root = VoxelWorld.rootNode,
        levels = VoxelWorld.levels,
        originX = originX,
        originY = originY,
        originZ = originZ,
        dirX = dirX,
        dirY = dirY,
        dirZ = dirZ,
        maxDistance = maxDistance,
    )

    /** As [cast], but against an explicitly supplied tree. */
    fun cast(
        nodes: IntArray,
        bricks: IntArray,
        root: Int,
        levels: Int,
        originX: Double,
        originY: Double,
        originZ: Double,
        dirX: Double,
        dirY: Double,
        dirZ: Double,
        maxDistance: Double,
    ): Boolean {
        hitDistance = 0.0
        hitMaterial = 0
        steps = 0

        // Brick units for the tree, block units for t, exactly as the shader does it.
        val ox = originX * INVERSE_EDGE
        val oy = originY * INVERSE_EDGE
        val oz = originZ * INVERSE_EDGE
        val dx = dirX * INVERSE_EDGE
        val dy = dirY * INVERSE_EDGE
        val dz = dirZ * INVERSE_EDGE

        val ix = 1.0 / guard(dx)
        val iy = 1.0 / guard(dy)
        val iz = 1.0 / guard(dz)

        val span = (1 shl levels).toDouble()
        var tEnter = 0.0
        var tLeave = maxDistance
        run {
            val ax = (0.0 - ox) * ix
            val bx = (span - ox) * ix
            val ay = (0.0 - oy) * iy
            val by = (span - oy) * iy
            val az = (0.0 - oz) * iz
            val bz = (span - oz) * iz
            tEnter = maxOf(minOf(ax, bx), minOf(ay, by), minOf(az, bz), 0.0)
            tLeave = minOf(maxOf(ax, bx), maxOf(ay, by), maxOf(az, bz), maxDistance)
        }
        if (tEnter > tLeave) {
            return false
        }

        var level = levels
        var cellX = 0
        var cellY = 0
        var cellZ = 0
        var node = root
        stack[level] = node

        var t = tEnter
        var nodeExit = tLeave

        var step = 0
        while (step < MAX_STEPS) {
            step++
            steps = step

            val childLevel = level - 1
            val size = (1 shl childLevel).toDouble()
            val advance = t + epsilon(t)
            var childX = Math.floor((ox + advance * dx) / size).toInt()
            var childY = Math.floor((oy + advance * dy) / size).toInt()
            var childZ = Math.floor((oz + advance * dz) / size).toInt()
            childX = childX.coerceIn(cellX * 2, cellX * 2 + 1)
            childY = childY.coerceIn(cellY * 2, cellY * 2 + 1)
            childZ = childZ.coerceIn(cellZ * 2, cellZ * 2 + 1)
            val slot = VoxelFormat.octantSlot(childX, childY, childZ)

            val masks = nodes[node * VoxelFormat.NODE_WORDS]
            val pointer = nodes[node * VoxelFormat.NODE_WORDS + 1]
            val childMask = VoxelFormat.childMask(masks)
            val childExit = minOf(
                cubeExit(ox, oy, oz, ix, iy, iz, childX, childY, childZ, childLevel),
                tLeave,
            )

            if (childMask and (1 shl slot) != 0) {
                val child = pointer + VoxelFormat.childRank(childMask, slot)
                if (VoxelFormat.internalMask(masks) and (1 shl slot) == 0) {
                    val base = nodes[child * VoxelFormat.NODE_WORDS]
                    if (traceBrick(bricks, base, childX, childY, childZ, ox, oy, oz, dirX, dirY, dirZ, t, childExit)) {
                        return true
                    }
                } else {
                    stack[childLevel] = child
                    level = childLevel
                    cellX = childX
                    cellY = childY
                    cellZ = childZ
                    node = child
                    nodeExit = childExit
                    continue
                }
            }

            t = maxOf(childExit, t + epsilon(t))
            if (t >= tLeave) {
                return false
            }
            while (level < levels && t >= nodeExit) {
                cellX = cellX shr 1
                cellY = cellY shr 1
                cellZ = cellZ shr 1
                level++
                node = stack[level]
                nodeExit = minOf(cubeExit(ox, oy, oz, ix, iy, iz, cellX, cellY, cellZ, level), tLeave)
            }
            if (t >= nodeExit) {
                return false
            }
        }
        return false
    }

    /** Convenience wrapper that casts towards a point rather than along a direction. */
    fun castTowards(
        originX: Double,
        originY: Double,
        originZ: Double,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        shorten: Double = 0.05,
    ): Boolean {
        val dx = targetX - originX
        val dy = targetY - originY
        val dz = targetZ - originZ
        val distance = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (distance < 1.0e-4) {
            return false
        }
        val inverse = 1.0 / distance
        return cast(
            originX, originY, originZ,
            dx * inverse, dy * inverse, dz * inverse,
            (distance - shorten).coerceAtLeast(0.0),
        )
    }

    private fun traceBrick(
        bricks: IntArray,
        base: Int,
        brickX: Int,
        brickY: Int,
        brickZ: Int,
        ox: Double,
        oy: Double,
        oz: Double,
        dirX: Double,
        dirY: Double,
        dirZ: Double,
        tEnter: Double,
        tExit: Double,
    ): Boolean {
        // Voxel units within this brick; direction is already one voxel per block travelled.
        val vx = (ox - brickX) * VoxelFormat.BRICK_EDGE
        val vy = (oy - brickY) * VoxelFormat.BRICK_EDGE
        val vz = (oz - brickZ) * VoxelFormat.BRICK_EDGE
        val ix = 1.0 / guard(dirX)
        val iy = 1.0 / guard(dirY)
        val iz = 1.0 / guard(dirZ)

        var t = tEnter
        var coarse = 0
        while (coarse < 24) {
            coarse++
            val advance = t + epsilon(t)
            val cx = Math.floor((vx + advance * dirX) * 0.25).toInt()
            val cy = Math.floor((vy + advance * dirY) * 0.25).toInt()
            val cz = Math.floor((vz + advance * dirZ) * 0.25).toInt()
            if (cx < 0 || cy < 0 || cz < 0 || cx > 3 || cy > 3 || cz > 3) {
                return false
            }

            val cellExit = boxExit(vx, vy, vz, ix, iy, iz, cx * 4.0, cy * 4.0, cz * 4.0, 4.0)

            if (coarseSolid(bricks, base, cx, cy, cz)) {
                var fine = t
                val limit = minOf(cellExit, tExit)
                var inner = 0
                while (inner < 16) {
                    inner++
                    val reach = fine + epsilon(fine)
                    val x = Math.floor(vx + reach * dirX).toInt().coerceIn(cx * 4, cx * 4 + 3)
                    val y = Math.floor(vy + reach * dirY).toInt().coerceIn(cy * 4, cy * 4 + 3)
                    val z = Math.floor(vz + reach * dirZ).toInt().coerceIn(cz * 4, cz * 4 + 3)

                    if (voxelSolid(bricks, base, x, y, z)) {
                        hitDistance = fine
                        hitMaterial = voxelSurface(bricks, base, x, y, z)
                        return true
                    }

                    val next = boxExit(vx, vy, vz, ix, iy, iz, x.toDouble(), y.toDouble(), z.toDouble(), 1.0)
                    fine = maxOf(next, fine + epsilon(fine))
                    if (fine >= limit) {
                        break
                    }
                }
            }

            t = maxOf(cellExit, t + epsilon(t))
            if (t >= tExit) {
                return false
            }
        }
        return false
    }

    private companion object {
        const val INVERSE_EDGE = 1.0 / VoxelFormat.BRICK_EDGE
        const val MAX_STEPS = 512

        fun guard(value: Double): Double =
            if (value >= 0.0) maxOf(value, 1.0e-12) else minOf(value, -1.0e-12)

        fun epsilon(t: Double): Double = 1.0e-5 + Math.abs(t) * 1.0e-9

        fun cubeExit(
            ox: Double, oy: Double, oz: Double,
            ix: Double, iy: Double, iz: Double,
            cellX: Int, cellY: Int, cellZ: Int, level: Int,
        ): Double {
            val size = (1 shl level).toDouble()
            return boxExit(ox, oy, oz, ix, iy, iz, cellX * size, cellY * size, cellZ * size, size)
        }

        fun boxExit(
            ox: Double, oy: Double, oz: Double,
            ix: Double, iy: Double, iz: Double,
            lowX: Double, lowY: Double, lowZ: Double, size: Double,
        ): Double {
            val ax = (lowX - ox) * ix
            val bx = (lowX + size - ox) * ix
            val ay = (lowY - oy) * iy
            val by = (lowY + size - oy) * iy
            val az = (lowZ - oz) * iz
            val bz = (lowZ + size - oz) * iz
            return minOf(maxOf(ax, bx), maxOf(ay, by), maxOf(az, bz))
        }

        fun voxelSolid(bricks: IntArray, base: Int, x: Int, y: Int, z: Int): Boolean {
            val bit = VoxelFormat.voxelBit(x, y, z)
            val word = bricks[base + VoxelFormat.OCCUPANCY_OFFSET + (bit ushr 5)]
            return word and (1 shl (bit and 31)) != 0
        }

        fun coarseSolid(bricks: IntArray, base: Int, x: Int, y: Int, z: Int): Boolean {
            val bit = VoxelFormat.coarseBit(x, y, z)
            val word = bricks[base + VoxelFormat.COARSE_OFFSET + (bit ushr 5)]
            return word and (1 shl (bit and 31)) != 0
        }

        /** Position of a voxel among the brick's solid voxels. Mirrors `svoSolidOrdinal`. */
        fun solidOrdinal(bricks: IntArray, base: Int, x: Int, y: Int, z: Int): Int {
            val bit = VoxelFormat.voxelBit(x, y, z)
            val word = bit ushr 5
            val group = word ushr 2
            val packed = bricks[base + VoxelFormat.PREFIX_OFFSET + (group ushr 1)]
            var index = if (group and 1 != 0) packed ushr 16 else packed and 0xFFFF
            for (preceding in (group shl 2) until word) {
                index += Integer.bitCount(bricks[base + VoxelFormat.OCCUPANCY_OFFSET + preceding])
            }
            val occupancy = bricks[base + VoxelFormat.OCCUPANCY_OFFSET + word]
            index += Integer.bitCount(occupancy and ((1 shl (bit and 31)) - 1))
            return index
        }

        /** The surface word of a solid voxel. Mirrors `svoPaletteEntry`. */
        fun voxelSurface(bricks: IntArray, base: Int, x: Int, y: Int, z: Int): Int {
            val header = bricks[base + VoxelFormat.PALETTE_HEADER_OFFSET]
            val count = VoxelFormat.paletteCount(header)
            val bits = VoxelFormat.paletteBits(header)

            var slot = 0
            if (bits > 0) {
                val ordinal = solidOrdinal(bricks, base, x, y, z)
                val perWord = 32 / bits
                val indexBase = base + VoxelFormat.PALETTE_OFFSET + count * VoxelFormat.PALETTE_ENTRY_WORDS
                val packed = bricks[indexBase + ordinal / perWord]
                slot = (packed ushr ((ordinal % perWord) * bits)) and ((1 shl bits) - 1)
            }
            return bricks[base + VoxelFormat.PALETTE_OFFSET + slot * VoxelFormat.PALETTE_ENTRY_WORDS]
        }
    }
}

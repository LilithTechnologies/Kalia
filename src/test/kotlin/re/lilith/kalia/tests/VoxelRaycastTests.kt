package re.lilith.kalia.tests

import re.lilith.kalia.voxel.VoxelFormat
import re.lilith.kalia.voxel.VoxelOctree
import re.lilith.kalia.voxel.VoxelRaycaster
import re.lilith.kalia.voxel.build.VoxelBrickBuilder
import re.lilith.kalia.voxel.pool.BrickArena
import re.lilith.kalia.voxel.pool.NodeArena
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the octree traversal against a brute-force walk of the same voxels.
 *
 * [VoxelRaycaster] is a line-for-line twin of the descent in `svo_common.glsl`, so agreement here
 * is the strongest evidence available that the shader traverses correctly too. The reference is a
 * plain Amanatides-Woo march over the dense voxel grid, which has no octree in it at all and can
 * therefore only disagree if the octree side is wrong.
 */
class VoxelRaycastTests {

    private class World(val levels: Int) {
        val nodeArena = NodeArena(4096, 1 shl 22)
        val brickArena = BrickArena(1 shl 16, 1 shl 24)
        val tree = VoxelOctree(levels, nodeArena)

        /** Dense reference set of solid voxel coordinates, in blocks. */
        val solid = HashSet<Long>()

        val voxels: Int get() = tree.span * VoxelFormat.BRICK_EDGE

        fun addBrick(brickX: Int, brickY: Int, brickZ: Int, fill: (Int, Int, Int) -> Boolean) {
            val builder = VoxelBrickBuilder()
            builder.reset()
            for (y in 0 until VoxelFormat.BRICK_EDGE) {
                for (z in 0 until VoxelFormat.BRICK_EDGE) {
                    for (x in 0 until VoxelFormat.BRICK_EDGE) {
                        if (!fill(x, y, z)) {
                            continue
                        }
                        builder.put(x, y, z, MATERIAL, LIGHT)
                        solid.add(
                            key(
                                brickX * VoxelFormat.BRICK_EDGE + x,
                                brickY * VoxelFormat.BRICK_EDGE + y,
                                brickZ * VoxelFormat.BRICK_EDGE + z,
                            ),
                        )
                    }
                }
            }
            val scratch = IntArray(VoxelFormat.MAX_BRICK_WORDS)
            val words = builder.build(scratch)
            if (words == 0) {
                return
            }
            val offset = brickArena.allocate(words)
            brickArena.write(offset, scratch, words)
            assertTrue(
                tree.insert(brickX, brickY, brickZ, offset, words, builder.averageColor565),
                "could not insert brick ($brickX,$brickY,$brickZ)",
            )
        }
    }

    @Test
    fun `traversal agrees with a dense march`() {
        val world = World(levels = 3)
        val random = Random(4242)

        // A floor, a wall with a gap in it, and some scattered noise: enough structure that rays
        // have to descend, pop back up and skip empty subtrees rather than hitting the first thing.
        for (bx in 0 until world.tree.span) {
            for (bz in 0 until world.tree.span) {
                world.addBrick(bx, 0, bz) { _, y, _ -> y < 4 }
            }
        }
        for (bz in 0 until world.tree.span) {
            world.addBrick(3, 1, bz) { x, y, _ -> x == 8 && y < 10 }
        }
        for (attempt in 0 until 40) {
            val bx = random.nextInt(world.tree.span)
            val by = 1 + random.nextInt(world.tree.span - 1)
            val bz = random.nextInt(world.tree.span)
            world.addBrick(bx, by, bz) { _, _, _ -> random.nextInt(24) == 0 }
        }

        var compared = 0
        for (attempt in 0 until 4000) {
            val ox = random.nextDouble() * world.voxels
            val oy = random.nextDouble() * world.voxels
            val oz = random.nextDouble() * world.voxels
            if (world.solid.contains(key(ox.toInt(), oy.toInt(), oz.toInt()))) {
                // Starting inside a voxel is not something the renderer does, and the reference
                // and the octree disagree about it by construction.
                continue
            }

            var dx = random.nextDouble() * 2.0 - 1.0
            var dy = random.nextDouble() * 2.0 - 1.0
            var dz = random.nextDouble() * 2.0 - 1.0
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            if (length < 1.0e-3) {
                continue
            }
            dx /= length
            dy /= length
            dz /= length

            val range = 200.0
            val caster = VoxelRaycaster()
            val hit = caster.cast(
                nodes = world.nodeArena.storage.words,
                bricks = world.brickArena.storage.words,
                root = world.tree.root,
                levels = world.levels,
                originX = ox, originY = oy, originZ = oz,
                dirX = dx, dirY = dy, dirZ = dz,
                maxDistance = range,
            )
            val reference = march(world, ox, oy, oz, dx, dy, dz, range)

            compared++
            assertEquals(
                reference >= 0.0,
                hit,
                "hit disagreement at ($ox,$oy,$oz) dir ($dx,$dy,$dz): reference=$reference",
            )
            if (hit) {
                assertTrue(
                    abs(caster.hitDistance - reference) < 0.05,
                    "distance disagreement: octree=${caster.hitDistance} reference=$reference",
                )
            }
        }
        assertTrue(compared > 3000, "only $compared rays were comparable")
    }

    @Test
    fun `axis aligned rays do not divide by zero`() {
        val world = World(levels = 2)
        world.addBrick(0, 0, 0) { _, y, _ -> y == 0 }

        val caster = VoxelRaycaster()
        val directions = listOf(
            doubleArrayOf(0.0, -1.0, 0.0),
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, -1.0),
        )
        for (direction in directions) {
            caster.cast(
                nodes = world.nodeArena.storage.words,
                bricks = world.brickArena.storage.words,
                root = world.tree.root,
                levels = world.levels,
                originX = 8.5, originY = 8.5, originZ = 8.5,
                dirX = direction[0], dirY = direction[1], dirZ = direction[2],
                maxDistance = 64.0,
            )
            assertTrue(caster.hitDistance.isFinite(), "non-finite distance for ${direction.toList()}")
        }

        // Straight down from the middle of the brick lands on the top face of the floor voxel.
        val down = caster.cast(
            nodes = world.nodeArena.storage.words,
            bricks = world.brickArena.storage.words,
            root = world.tree.root,
            levels = world.levels,
            originX = 8.5, originY = 8.5, originZ = 8.5,
            dirX = 0.0, dirY = -1.0, dirZ = 0.0,
            maxDistance = 64.0,
        )
        assertTrue(down, "a ray dropped onto the floor missed it")
        assertTrue(abs(caster.hitDistance - 7.5) < 0.05, "floor hit at ${caster.hitDistance}, expected 7.5")
    }

    @Test
    fun `an empty tree is always a miss`() {
        val world = World(levels = 3)
        val caster = VoxelRaycaster()
        assertTrue(
            !caster.cast(
                nodes = world.nodeArena.storage.words,
                bricks = world.brickArena.storage.words,
                root = world.tree.root,
                levels = world.levels,
                originX = 1.0, originY = 1.0, originZ = 1.0,
                dirX = 0.577, dirY = 0.577, dirZ = 0.577,
                maxDistance = 500.0,
            ),
        )
    }

    /** Amanatides-Woo over the dense grid. Returns the hit distance, or -1 for a miss. */
    private fun march(
        world: World,
        ox: Double, oy: Double, oz: Double,
        dx: Double, dy: Double, dz: Double,
        range: Double,
    ): Double {
        var x = Math.floor(ox).toInt()
        var y = Math.floor(oy).toInt()
        var z = Math.floor(oz).toInt()

        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1

        val deltaX = if (dx != 0.0) abs(1.0 / dx) else Double.MAX_VALUE
        val deltaY = if (dy != 0.0) abs(1.0 / dy) else Double.MAX_VALUE
        val deltaZ = if (dz != 0.0) abs(1.0 / dz) else Double.MAX_VALUE

        var nextX = if (dx != 0.0) ((if (dx > 0) x + 1 else x) - ox) / dx else Double.MAX_VALUE
        var nextY = if (dy != 0.0) ((if (dy > 0) y + 1 else y) - oy) / dy else Double.MAX_VALUE
        var nextZ = if (dz != 0.0) ((if (dz > 0) z + 1 else z) - oz) / dz else Double.MAX_VALUE

        var travelled = 0.0
        val limit = world.voxels
        while (travelled <= range) {
            if (x in 0 until limit && y in 0 until limit && z in 0 until limit &&
                world.solid.contains(key(x, y, z))
            ) {
                return travelled
            }
            if (nextX < nextY && nextX < nextZ) {
                travelled = nextX
                nextX += deltaX
                x += stepX
            } else if (nextY < nextZ) {
                travelled = nextY
                nextY += deltaY
                y += stepY
            } else {
                travelled = nextZ
                nextZ += deltaZ
                z += stepZ
            }
            // Once the ray has left the volume for good there is nothing more to find.
            if ((x < 0 && stepX < 0) || (x >= limit && stepX > 0) ||
                (y < 0 && stepY < 0) || (y >= limit && stepY > 0) ||
                (z < 0 && stepZ < 0) || (z >= limit && stepZ > 0)
            ) {
                return -1.0
            }
        }
        return -1.0
    }

    private companion object {
        /** One arbitrary surface; these tests only care about where rays land, not what they hit. */
        /** Full sky, no block light. */
        const val LIGHT = 0xF0

        val MATERIAL: Long = (VoxelFormat.surfaceWord(1, 2, 0, 0).toLong() and 0xFFFFFFFFL) or
            (VoxelFormat.tintWord(3, 0xFFF, 0x7).toLong() shl 32)

        fun key(x: Int, y: Int, z: Int): Long =
            (x.toLong() and 0xFFFF shl 32) or (y.toLong() and 0xFFFF shl 16) or (z.toLong() and 0xFFFF)
    }
}

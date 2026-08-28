package re.lilith.kalia.tests

import re.lilith.kalia.voxel.VoxelFormat
import re.lilith.kalia.voxel.VoxelOctree
import re.lilith.kalia.voxel.build.VoxelBrickBuilder
import re.lilith.kalia.voxel.pool.BrickArena
import re.lilith.kalia.voxel.pool.NodeArena
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

import kotlin.test.assertTrue

/**
 * Exercises the data structures the tracer reads.
 *
 * The decoders here are deliberate re-implementations of what `svo_common.glsl` does, so a change
 * to the packing on either side that is not mirrored on the other shows up as a failure rather than
 * as corrupted geometry on somebody's screen.
 */
class VoxelStructureTests {

    @Test
    fun `brick round-trips every voxel it was given`() {
        // Enough distinct surfaces to force the packer onto its widest index, so the bit twiddling
        // in the palette writer and the shader-side reader are both exercised properly.
        for (surfaces in intArrayOf(1, 2, 3, 5, 17, 200)) {
            roundTrip(surfaces)
        }
    }

    private fun roundTrip(surfaces: Int) {
        val random = Random(20250828L + surfaces)
        val builder = VoxelBrickBuilder()
        val expected = HashMap<Int, Long>()

        val palette = LongArray(surfaces) { index ->
            entry(
                spriteTop = index * 3 + 1,
                spriteSide = index * 3 + 2,
                spriteBottom = index * 3 + 3,
                emission = index and 0xF,
                flags = (index shr 1) and 0xF,
                tint = index and 0xFFF,
            )
        }

        repeat(1200) {
            val x = random.nextInt(16)
            val y = random.nextInt(16)
            val z = random.nextInt(16)
            val chosen = palette[random.nextInt(surfaces)]
            builder.put(x, y, z, chosen, random.nextInt(256))
            expected[VoxelFormat.voxelBit(x, y, z)] = chosen
        }

        val words = IntArray(VoxelFormat.MAX_BRICK_WORDS)
        val length = builder.build(words)

        assertEquals(expected.size, builder.solid, "solid count disagrees with the voxels written")
        assertTrue(length in 1..VoxelFormat.MAX_BRICK_WORDS, "brick length $length is out of range")
        assertTrue(builder.palette <= surfaces, "palette grew past the surfaces supplied")

        for (bit in 0 until VoxelFormat.BRICK_VOXELS) {
            val x = bit and 15
            val z = (bit shr 4) and 15
            val y = (bit shr 8) and 15
            val solid = decodeSolid(words, x, y, z)
            val want = expected[bit]
            if (want == null) {
                assertFalse(solid, "voxel ($x,$y,$z) should be empty")
                continue
            }
            assertTrue(solid, "voxel ($x,$y,$z) should be solid")
            assertEquals(
                want,
                decodeEntry(words, x, y, z),
                "surface at ($x,$y,$z) is wrong with $surfaces palette entries",
            )
        }
    }

    @Test
    fun `a uniform brick spends no bits on indices`() {
        val builder = VoxelBrickBuilder()
        for (y in 0 until 16) {
            for (z in 0 until 16) {
                for (x in 0 until 16) {
                    builder.put(x, y, z, STONE, 0xF0)
                }
            }
        }
        val words = IntArray(VoxelFormat.MAX_BRICK_WORDS)
        val length = builder.build(words)

        val header = words[VoxelFormat.PALETTE_HEADER_OFFSET]
        assertEquals(1, VoxelFormat.paletteCount(header))
        assertEquals(0, VoxelFormat.paletteBits(header), "one surface needs no index bits")
        assertEquals(
            VoxelFormat.PALETTE_OFFSET + VoxelFormat.PALETTE_ENTRY_WORDS,
            length,
            "a solid section of one block type should cost only its header and one palette entry",
        )
        assertEquals(STONE, decodeEntry(words, 7, 7, 7))
    }

    @Test
    fun `coarse mask covers exactly the occupied cells`() {
        val builder = VoxelBrickBuilder()
        builder.put(0, 0, 0, MATERIAL, 0)
        builder.put(15, 15, 15, MATERIAL, 0)
        builder.put(6, 9, 3, MATERIAL, 0)

        val words = IntArray(VoxelFormat.MAX_BRICK_WORDS)
        builder.build(words)

        val occupied = setOf(
            VoxelFormat.coarseBit(0, 0, 0),
            VoxelFormat.coarseBit(3, 3, 3),
            VoxelFormat.coarseBit(1, 2, 0),
        )
        for (bit in 0 until 64) {
            val set = decodeCoarse(words, bit)
            assertEquals(bit in occupied, set, "coarse cell $bit")
        }
    }

    @Test
    fun `empty brick produces no words`() {
        val builder = VoxelBrickBuilder()
        assertEquals(0, builder.build(IntArray(VoxelFormat.MAX_BRICK_WORDS)))
    }

    @Test
    fun `octree stores and finds every leaf`() {
        val arena = NodeArena(1024, 1 shl 20)
        val tree = VoxelOctree(levels = 5, arena = arena)
        val random = Random(1234)
        val placed = HashMap<Int, Int>()

        repeat(600) { index ->
            val x = random.nextInt(tree.span)
            val y = random.nextInt(tree.span)
            val z = random.nextInt(tree.span)
            val offset = (index + 1) * 64
            assertTrue(tree.insert(x, y, z, offset, 1, 0x1234), "insert ($x,$y,$z) failed")
            placed[key(x, y, z)] = offset
        }

        for ((packed, offset) in placed) {
            val x = packed and 63
            val y = (packed shr 6) and 63
            val z = (packed shr 12) and 63
            assertEquals(offset, tree.leafBrick(x, y, z), "leaf at ($x,$y,$z)")
            assertEquals(offset, descend(arena, tree, x, y, z), "shader-style descent at ($x,$y,$z)")
        }
    }

    @Test
    fun `removing leaves prunes without disturbing the rest`() {
        val arena = NodeArena(1024, 1 shl 20)
        val tree = VoxelOctree(levels = 4, arena = arena)
        val random = Random(99)
        val placed = LinkedHashMap<Int, Int>()

        repeat(400) { index ->
            val x = random.nextInt(tree.span)
            val y = random.nextInt(tree.span)
            val z = random.nextInt(tree.span)
            tree.insert(x, y, z, (index + 1) * 32, 1, 0)
            placed[key(x, y, z)] = (index + 1) * 32
        }

        val survivors = LinkedHashMap(placed)
        for (packed in placed.keys.toList()) {
            if (random.nextInt(3) != 0) {
                continue
            }
            val x = packed and 63
            val y = (packed shr 6) and 63
            val z = (packed shr 12) and 63
            assertTrue(tree.remove(x, y, z), "remove ($x,$y,$z) reported nothing there")
            survivors.remove(packed)

            assertEquals(
                NodeArena.NO_NODE,
                if (tree.leafBrick(x, y, z) == NodeArena.NO_NODE) NodeArena.NO_NODE else 0,
                "leaf at ($x,$y,$z) survived removal",
            )
        }

        for ((packed, offset) in survivors) {
            val x = packed and 63
            val y = (packed shr 6) and 63
            val z = (packed shr 12) and 63
            assertEquals(offset, tree.leafBrick(x, y, z), "surviving leaf at ($x,$y,$z) moved or vanished")
        }

        // Emptying the tree entirely has to leave the root and nothing else.
        for (packed in survivors.keys) {
            tree.remove(packed and 63, (packed shr 6) and 63, (packed shr 12) and 63)
        }
        assertEquals(0, VoxelFormat.childMask(arena.masks(tree.root)), "root still has children")
    }

    @Test
    fun `node runs are recycled rather than leaked`() {
        val arena = NodeArena(1024, 1 shl 20)
        val tree = VoxelOctree(levels = 4, arena = arena)

        repeat(200) { index ->
            val x = index % tree.span
            val y = (index / tree.span) % tree.span
            tree.insert(x, y, 0, index + 1, 1, 0)
        }
        val peak = arena.usedNodes

        repeat(20) { round ->
            repeat(200) { index ->
                tree.remove(index % tree.span, (index / tree.span) % tree.span, 0)
            }
            repeat(200) { index ->
                tree.insert(index % tree.span, (index / tree.span) % tree.span, 0, index + 1, 1, 0)
            }
            assertEquals(
                peak,
                arena.usedNodes,
                "the arena grew on round $round; freed runs are not coming back",
            )
        }
    }

    @Test
    fun `brick arena reuses freed records`() {
        val arena = BrickArena(1 shl 16, 1 shl 22)
        val first = arena.allocate(300)
        arena.free(first, 300)
        assertEquals(first, arena.allocate(300), "a same-sized allocation should land back in place")

        val other = arena.allocate(VoxelFormat.MAX_BRICK_WORDS)
        assertTrue(other != first)
        assertEquals(2, arena.liveBricks)
    }

    // -- decoders mirroring svo_common.glsl -------------------------------------------------------

    private fun decodeSolid(words: IntArray, x: Int, y: Int, z: Int): Boolean {
        val bit = VoxelFormat.voxelBit(x, y, z)
        val word = words[VoxelFormat.OCCUPANCY_OFFSET + (bit ushr 5)]
        return word and (1 shl (bit and 31)) != 0
    }

    private fun decodeCoarse(words: IntArray, bit: Int): Boolean {
        val word = words[VoxelFormat.COARSE_OFFSET + (bit ushr 5)]
        return word and (1 shl (bit and 31)) != 0
    }

    private fun decodeEntry(words: IntArray, x: Int, y: Int, z: Int): Long {
        val header = words[VoxelFormat.PALETTE_HEADER_OFFSET]
        val count = VoxelFormat.paletteCount(header)
        val bits = VoxelFormat.paletteBits(header)

        var slot = 0
        if (bits > 0) {
            val bit = VoxelFormat.voxelBit(x, y, z)
            val word = bit ushr 5
            val group = word ushr 2
            val packed = words[VoxelFormat.PREFIX_OFFSET + (group ushr 1)]
            var ordinal = if (group and 1 != 0) packed ushr 16 else packed and 0xFFFF
            for (preceding in (group shl 2) until word) {
                ordinal += Integer.bitCount(words[VoxelFormat.OCCUPANCY_OFFSET + preceding])
            }
            ordinal += Integer.bitCount(words[VoxelFormat.OCCUPANCY_OFFSET + word] and ((1 shl (bit and 31)) - 1))

            val perWord = 32 / bits
            val indexBase = VoxelFormat.PALETTE_OFFSET + count * VoxelFormat.PALETTE_ENTRY_WORDS
            slot = (words[indexBase + ordinal / perWord] ushr ((ordinal % perWord) * bits)) and ((1 shl bits) - 1)
        }

        val base = VoxelFormat.PALETTE_OFFSET + slot * VoxelFormat.PALETTE_ENTRY_WORDS
        return (words[base].toLong() and 0xFFFFFFFFL) or (words[base + 1].toLong() shl 32)
    }

    /** The descent the shader performs, expressed against the arena directly. */
    private fun descend(arena: NodeArena, tree: VoxelOctree, x: Int, y: Int, z: Int): Int {
        var node = tree.root
        for (level in tree.levels downTo 1) {
            val shift = level - 1
            val slot = ((x ushr shift) and 1) or
                (((y ushr shift) and 1) shl 1) or
                (((z ushr shift) and 1) shl 2)
            val masks = arena.masks(node)
            val childMask = (masks ushr 8) and 0xFF
            if (childMask and (1 shl slot) == 0) {
                return NodeArena.NO_NODE
            }
            node = arena.pointer(node) + Integer.bitCount(childMask and ((1 shl slot) - 1))
        }
        return arena.masks(node)
    }

    private fun key(x: Int, y: Int, z: Int): Int = x or (y shl 6) or (z shl 12)

    private companion object {
        fun entry(
            spriteTop: Int,
            spriteSide: Int,
            spriteBottom: Int,
            emission: Int,
            flags: Int,
            tint: Int,
        ): Long {
            val surface = VoxelFormat.surfaceWord(spriteTop, spriteSide, emission, flags)
            val tintWord = VoxelFormat.tintWord(spriteBottom, tint, 0x7)
            return (surface.toLong() and 0xFFFFFFFFL) or (tintWord.toLong() shl 32)
        }

        val MATERIAL = entry(1, 2, 3, 0, 0, 0xFFF)
        val STONE = entry(9, 9, 9, 0, 0, 0xFFF)
    }
}

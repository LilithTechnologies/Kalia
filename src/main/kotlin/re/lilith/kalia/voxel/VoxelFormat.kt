package re.lilith.kalia.voxel

/**
 * Binary layout shared by the CPU builder and the GLSL traversal.
 *
 * The world is decomposed into 16x16x16 *bricks*, one per Minecraft chunk section, and those
 * bricks are the leaves of a sparse voxel octree. A brick holds a bitmask pyramid, a small palette
 * of the surfaces it actually contains, and one palette index per solid voxel packed at the
 * narrowest width that fits.
 *
 * The palette is what makes this affordable. A section of solid stone is one palette entry and zero
 * index bits, so it costs 596 bytes no matter how many voxels it holds; a busy surface section with
 * sixteen block types costs under two kilobytes. Storing a material word per voxel instead would be
 * an order of magnitude worse, and most of a loaded world is underground.
 *
 * Every offset in here is measured in 32-bit words, because that is what a `uint[]` storage buffer
 * indexes with.
 */
object VoxelFormat {
    /** Edge length of a brick, in voxels. Matches a Minecraft chunk section. */
    const val BRICK_EDGE = 16
    const val BRICK_VOXELS = BRICK_EDGE * BRICK_EDGE * BRICK_EDGE

    /** Edge length of the coarse acceleration grid inside a brick. */
    const val COARSE_EDGE = 4

    /** Voxels per coarse cell along one axis. */
    const val COARSE_SPAN = BRICK_EDGE / COARSE_EDGE

    /** `4^3` occupancy bits, one per coarse cell. */
    const val COARSE_WORDS = 2

    /** `16^3` occupancy bits. */
    const val OCCUPANCY_WORDS = BRICK_VOXELS / 32

    /** Occupancy words covered by a single prefix entry. */
    const val PREFIX_GROUP = 4

    /** Packed `uint16` running popcounts, two per word. */
    const val PREFIX_WORDS = OCCUPANCY_WORDS / PREFIX_GROUP / 2

    const val COARSE_OFFSET = 0
    const val OCCUPANCY_OFFSET = COARSE_OFFSET + COARSE_WORDS
    const val PREFIX_OFFSET = OCCUPANCY_OFFSET + OCCUPANCY_WORDS

    /** `paletteCount` in the low half, `bitsPerIndex` in the high half. */
    const val PALETTE_HEADER_OFFSET = PREFIX_OFFSET + PREFIX_WORDS

    /**
     * `solidCount:16 | lightBits:8 | uniformLight:8`.
     *
     * The solid count is here because the light array sits behind the variable-length index array,
     * and the shader needs to know how long that is to find it.
     */
    const val LIGHT_INFO_OFFSET = PALETTE_HEADER_OFFSET + 1

    const val PALETTE_OFFSET = LIGHT_INFO_OFFSET + 1

    /** Words per palette entry: one for the face sprites, one for the tint. */
    const val PALETTE_ENTRY_WORDS = 2

    fun lightInfo(solidCount: Int, lightBits: Int, uniformLight: Int): Int =
        (solidCount and 0xFFFF) or ((lightBits and 0xFF) shl 16) or ((uniformLight and 0xFF) shl 24)

    fun solidCount(info: Int): Int = info and 0xFFFF

    fun lightBits(info: Int): Int = (info ushr 16) and 0xFF

    fun uniformLight(info: Int): Int = (info ushr 24) and 0xFF

    /** Vanilla's two light channels, four bits each, as one byte. */
    fun packLight(sky: Int, block: Int): Int = ((sky and 0xF) shl 4) or (block and 0xF)

    /**
     * Distinct surfaces a single section may hold. Real sections rarely pass a dozen; the cap only
     * bounds the worst case, and anything beyond it collapses onto the last entry.
     */
    const val MAX_PALETTE = 256

    /** Index widths the packer will choose between. Zero means every voxel shares one entry. */
    val INDEX_WIDTHS = intArrayOf(0, 1, 2, 4, 8)

    /** Header, full palette, eight-bit indices and a byte of light per voxel. */
    const val MAX_BRICK_WORDS =
        PALETTE_OFFSET + MAX_PALETTE * PALETTE_ENTRY_WORDS + BRICK_VOXELS * 8 / 32 + BRICK_VOXELS * 8 / 32

    /**
     * Bit index of a voxel within the occupancy mask. Y-major so that a horizontal slice is
     * contiguous, which is the access pattern of both the builder and the in-brick DDA.
     */
    fun voxelBit(x: Int, y: Int, z: Int): Int = (y shl 8) or (z shl 4) or x

    fun coarseBit(x: Int, y: Int, z: Int): Int = (y shl 4) or (z shl 2) or x

    fun paletteHeader(count: Int, bitsPerIndex: Int): Int = (count and 0xFFFF) or (bitsPerIndex shl 16)

    fun paletteCount(header: Int): Int = header and 0xFFFF

    fun paletteBits(header: Int): Int = header ushr 16

    /** Narrowest supported width that can address [count] entries. */
    fun bitsFor(count: Int): Int {
        for (width in INDEX_WIDTHS) {
            if (count <= (1 shl width)) {
                return width
            }
        }
        return 8
    }

    // -- palette entries -------------------------------------------------------------------------

    /**
     * First word: the sprite each face samples, plus how the surface behaves.
     *
     * Three sprites rather than one because a grass block is green on top, grassy on the sides and
     * dirt underneath, and a single texture per voxel gets all three wrong at once.
     */
    fun surfaceWord(spriteTop: Int, spriteSide: Int, emission: Int, flags: Int): Int =
        (spriteTop and SPRITE_MASK) or
            ((spriteSide and SPRITE_MASK) shl 12) or
            ((emission and 0xF) shl 24) or
            ((flags and 0xF) shl 28)

    /**
     * Second word: the bottom face's sprite, the tint, and which faces the tint applies to.
     *
     * The mask matters because a grass block's side is two quads stacked, an untinted dirt-and-
     * grass texture under a tinted overlay. Tinting the whole block turns the sides solid green,
     * which is exactly the wrong half of that pair.
     */
    fun tintWord(spriteBottom: Int, tint444: Int, tintedFaces: Int): Int =
        (spriteBottom and SPRITE_MASK) or
            ((tint444 and 0xFFF) shl 12) or
            ((tintedFaces and 0x7) shl 24)

    const val TINT_FACE_TOP = 1
    const val TINT_FACE_SIDE = 2
    const val TINT_FACE_BOTTOM = 4

    fun tintedFaces(tint: Int): Int = (tint ushr 24) and 0x7

    fun spriteTop(surface: Int): Int = surface and SPRITE_MASK
    fun spriteSide(surface: Int): Int = (surface ushr 12) and SPRITE_MASK
    fun spriteBottom(tint: Int): Int = tint and SPRITE_MASK
    fun emission(surface: Int): Int = (surface ushr 24) and 0xF
    fun flags(surface: Int): Int = (surface ushr 28) and 0xF
    fun tint444(tint: Int): Int = (tint ushr 12) and 0xFFF

    /** Sprite indices are 12 bits, which is ten times what a vanilla block atlas holds. */
    const val SPRITE_MASK = 0xFFF
    const val MAX_SPRITES = SPRITE_MASK + 1

    fun pack444(red: Int, green: Int, blue: Int): Int =
        (((red shr 4) and 0xF) shl 8) or (((green shr 4) and 0xF) shl 4) or ((blue shr 4) and 0xF)

    /** Light passes through, but the surface still shades and tints what is behind it. */
    const val FLAG_TRANSLUCENT = 1

    /** Mirror-like: the reflection pass traces a specular ray off this surface. */
    const val FLAG_REFLECTIVE = 2

    /** Foliage and other alpha-tested geometry, which only partially occludes. */
    const val FLAG_FOLIAGE = 4

    /** A fluid surface. */
    const val FLAG_FLUID = 8

    // -- octree nodes --------------------------------------------------------------------------

    /**
     * Words per octree node. A node is a `uvec2`: the masks and either a child pointer (internal)
     * or a brick pointer (leaf).
     */
    const val NODE_WORDS = 2

    /**
     * Largest octree the anchor logic will build, as a power-of-two count of bricks per axis.
     * Level 7 spans 128 bricks, i.e. 2048 blocks, which comfortably contains any render distance
     * the game offers while keeping the descent to seven steps.
     */
    const val MAX_LEVELS = 7

    /**
     * Packs the two 8-bit masks the way the traversal wants them: the child mask in bits 8..15 so
     * that `masks << (7 - slot)` lands the current child's presence bit on `0x8000`, and the
     * "child is an internal node" mask in bits 0..7 so the same shift lands it on `0x0080`.
     */
    fun nodeMasks(childMask: Int, internalMask: Int): Int =
        ((childMask and 0xFF) shl 8) or (internalMask and 0xFF)

    fun childMask(masks: Int): Int = (masks ushr 8) and 0xFF

    fun internalMask(masks: Int): Int = masks and 0xFF

    /** Index of a child within its parent's contiguous run, given the parent's child mask. */
    fun childRank(childMask: Int, slot: Int): Int = Integer.bitCount(childMask and ((1 shl slot) - 1))

    fun octantSlot(x: Int, y: Int, z: Int): Int = (x and 1) or ((y and 1) shl 1) or ((z and 1) shl 2)
}

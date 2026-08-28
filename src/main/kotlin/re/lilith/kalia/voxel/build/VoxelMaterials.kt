package re.lilith.kalia.voxel.build

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.material.Material
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.util.math.Direction
import re.lilith.kalia.voxel.VoxelFormat

/**
 * Turns block states into the two-word palette entry the tracer shades with.
 *
 * The texture for each face comes out of the block's own baked model: the quad vertex data already
 * carries atlas coordinates, so reading the min and max of a face's UVs recovers exactly the
 * rectangle the rasteriser would have sampled, without needing the sprite object or a parallel
 * texture registry.
 *
 * Resolution is cached per raw block state id, so the chunk build threads pay one array read per
 * voxel instead of walking Minecraft's model machinery 4096 times a section. The table is written
 * racily on purpose: every thread computes the same value, so a duplicated computation is harmless
 * and locking would sit right in the hottest loop of the voxeliser.
 */
object VoxelMaterials {
    /** Raw ids in 1.8.9 are `blockId shl 4 or metadata`, so the table covers every possible state. */
    private const val TABLE_SIZE = 1 shl 16

    /** Sentinel meaning "not resolved yet". A real entry always has its high word populated. */
    private const val UNRESOLVED = 0L

    /** Encoded value for "this block does not participate in the voxel scene at all". */
    const val EMPTY: Long = -1L

    private val table = LongArray(TABLE_SIZE)

    fun reset() {
        java.util.Arrays.fill(table, UNRESOLVED)
        VoxelSprites.reset()
    }

    /**
     * @return the packed palette entry, surface word in the low half and tint word in the high
     *         half, or [EMPTY] when the block should not become a voxel.
     */
    fun of(state: BlockState): Long {
        val raw = Block.getByBlockState(state)
        if (raw !in 0..<TABLE_SIZE) {
            return resolve(state)
        }
        val cached = table[raw]
        if (cached != UNRESOLVED) {
            return cached
        }
        val resolved = resolve(state)
        table[raw] = resolved
        return resolved
    }

    fun surfaceOf(entry: Long): Int = entry.toInt()

    fun tintOf(entry: Long): Int = (entry ushr 32).toInt()

    private fun pack(surface: Int, tint: Int): Long =
        (surface.toLong() and 0xFFFFFFFFL) or (tint.toLong() shl 32)

    private fun resolve(state: BlockState): Long {
        val block = state.block ?: return EMPTY
        val material = block.material ?: return EMPTY
        if (material === Material.AIR) {
            return EMPTY
        }

        val fluid = material.isFluid
        if (!fluid && !occupies(block, material)) {
            return EMPTY
        }

        val id = Block.getIdByBlock(block)
        val model = runCatching {
            MinecraftClient.getInstance()?.blockRenderManager?.models?.getBakedModel(state)
        }.getOrNull()

        // Fluids have no baked model in 1.8.9; they are drawn by a dedicated renderer that looks
        // its sprites up by name. Without this they resolve to the missing-texture checkerboard.
        val fluidSprite = if (fluid) fluidSprite(id) else NO_SPRITE
        val fallback = if (fluidSprite != NO_SPRITE) fluidSprite else fallbackSprite(model)

        val top = spriteFor(model, Direction.UP, fallback)
        val side = spriteFor(model, Direction.NORTH, fallback)
        val bottom = spriteFor(model, Direction.DOWN, fallback)

        var flags = 0
        if (fluid) {
            flags = flags or VoxelFormat.FLAG_FLUID
        }
        if (fluid && id != LAVA_STILL && id != LAVA_FLOWING) {
            flags = flags or VoxelFormat.FLAG_TRANSLUCENT or VoxelFormat.FLAG_REFLECTIVE
        }
        if (id in TRANSLUCENT_IDS) {
            flags = flags or VoxelFormat.FLAG_TRANSLUCENT
        }
        if (id in REFLECTIVE_IDS) {
            flags = flags or VoxelFormat.FLAG_REFLECTIVE
        }
        if (material === Material.FOLIAGE || material === Material.PLANT ||
            material === Material.REPLACEABLE_PLANT || !block.isFullCube
        ) {
            flags = flags or VoxelFormat.FLAG_FOLIAGE
        }

        val emission = emissionOf(block, id)
        val tint = tintFor(id, material)

        var tintedFaces = 0
        if (top.tinted) {
            tintedFaces = tintedFaces or VoxelFormat.TINT_FACE_TOP
        }
        if (side.tinted) {
            tintedFaces = tintedFaces or VoxelFormat.TINT_FACE_SIDE
        }
        if (bottom.tinted) {
            tintedFaces = tintedFaces or VoxelFormat.TINT_FACE_BOTTOM
        }
        if (fluid) {
            // The fluid renderer always applies the biome colour, and it has no quads to say so.
            tintedFaces = VoxelFormat.TINT_FACE_TOP or VoxelFormat.TINT_FACE_SIDE or VoxelFormat.TINT_FACE_BOTTOM
        }

        return pack(
            VoxelFormat.surfaceWord(top.sprite, side.sprite, emission, flags),
            VoxelFormat.tintWord(bottom.sprite, tint, tintedFaces),
        )
    }

    private class Face(val sprite: Int, val tinted: Boolean)

    /**
     * Reads the atlas rectangle a face samples straight out of the baked quad's vertex data.
     *
     * A 1.8.9 quad is seven ints per vertex, with the texture coordinate at offsets four and five,
     * so the extent of a face's UVs across its four vertices is the sprite it uses.
     *
     * Where a face has several quads the untinted one wins. That is the base layer in every vanilla
     * case that matters — grass sides, mycelium, snowy blocks — and the tinted quad stacked over it
     * is an overlay that a single sprite per face cannot represent anyway.
     */
    private fun spriteFor(model: BakedModel?, face: Direction, fallback: Int): Face {
        val quads = runCatching { model?.getByDirection(face) }.getOrNull()
        if (quads.isNullOrEmpty()) {
            return Face(fallback, false)
        }
        quads.firstOrNull { !it.hasColor() }?.let { plain ->
            internRectangle(plain)?.let { return Face(it, false) }
        }
        val first = quads.first()
        return Face(internRectangle(first) ?: fallback, first.hasColor())
    }

    private fun fallbackSprite(model: BakedModel?): Int {
        val general = runCatching { model?.quads }.getOrNull()?.firstOrNull()
        if (general != null) {
            internRectangle(general)?.let { return it }
        }
        val particle = runCatching { model?.particleSprite }.getOrNull() ?: return NO_SPRITE
        return VoxelSprites.intern(particle.minU, particle.minV, particle.maxU, particle.maxV)
    }

    /** Looks a fluid's still texture up on the atlas by name. */
    private fun fluidSprite(id: Int): Int {
        val name = when (id) {
            WATER_STILL, WATER_FLOWING -> "minecraft:blocks/water_still"
            LAVA_STILL, LAVA_FLOWING -> "minecraft:blocks/lava_still"
            else -> return NO_SPRITE
        }
        val sprite = runCatching {
            MinecraftClient.getInstance()?.spriteAtlasTexture?.getSprite(name)
        }.getOrNull() ?: return NO_SPRITE
        if (sprite.maxU <= sprite.minU || sprite.maxV <= sprite.minV) {
            return NO_SPRITE
        }
        return VoxelSprites.intern(sprite.minU, sprite.minV, sprite.maxU, sprite.maxV)
    }

    private fun internRectangle(quad: BakedQuad): Int? {
        val data = quad.vertexData ?: return null
        if (data.size < VERTEX_STRIDE * 4) {
            return null
        }
        var minU = Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (vertex in 0 until 4) {
            val u = java.lang.Float.intBitsToFloat(data[vertex * VERTEX_STRIDE + UV_OFFSET])
            val v = java.lang.Float.intBitsToFloat(data[vertex * VERTEX_STRIDE + UV_OFFSET + 1])
            if (!u.isFinite() || !v.isFinite()) {
                return null
            }
            minU = minOf(minU, u)
            minV = minOf(minV, v)
            maxU = maxOf(maxU, u)
            maxV = maxOf(maxV, v)
        }
        if (maxU <= minU || maxV <= minV) {
            return null
        }
        return VoxelSprites.intern(minU, minV, maxU, maxV)
    }

    /**
     * Whether the block fills enough of its cell to be worth a voxel. Full cubes obviously qualify;
     * so do stairs, slabs and fences, which read as solid for shadowing even though they are not
     * cubes. Torches, rails and crops are left out, because turning them into whole voxels reads as
     * a floating block in every shadow and reflection.
     */
    private fun occupies(block: Block, material: Material): Boolean {
        if (block.isFullCube) {
            return true
        }
        if (!material.isSolid || !material.blocksMovement()) {
            return false
        }
        val height = block.maxY - block.minY
        val width = block.maxX - block.minX
        val depth = block.maxZ - block.minZ
        return height * width * depth >= 0.2
    }

    private fun emissionOf(block: Block, id: Int): Int {
        val light = block.lightLevel
        if (light > 0) {
            return light.coerceAtMost(15)
        }
        return if (id == LAVA_STILL || id == LAVA_FLOWING) 15 else 0
    }

    /**
     * The colour a tinted face is multiplied by.
     *
     * The rasteriser would have looked this up per position from the biome; the voxel scene has no
     * biome at trace time, so each block gets one representative colour. Which faces it applies to
     * is decided per quad, not here.
     */
    private fun tintFor(id: Int, material: Material): Int {
        TINTS[id]?.let { return it }
        return when (material) {
            Material.FOLIAGE, Material.PLANT, Material.REPLACEABLE_PLANT -> FOLIAGE_TINT
            Material.WATER -> WATER_TINT
            Material.GRASS -> GRASS_TINT
            else -> WHITE
        }
    }

    /** Seven ints per vertex in 1.8.9: position, colour, texture, light. */
    private const val VERTEX_STRIDE = 7
    private const val UV_OFFSET = 4

    private const val WATER_STILL = 9
    private const val WATER_FLOWING = 8
    private const val LAVA_FLOWING = 10
    private const val LAVA_STILL = 11

    private val WHITE = VoxelFormat.pack444(255, 255, 255)
    private val GRASS_TINT = VoxelFormat.pack444(0x79, 0xC0, 0x5A)
    private val FOLIAGE_TINT = VoxelFormat.pack444(0x59, 0xAE, 0x30)
    private val WATER_TINT = VoxelFormat.pack444(0x3F, 0x76, 0xE4)

    /** Glass, stained glass, panes, ice and cobwebs: light passes through but comes out tinted. */
    private val TRANSLUCENT_IDS = setOf(20, 95, 102, 160, 79, 174, 30)

    /** Ice and the polished metal/gem blocks, which earn a specular ray in the reflection pass. */
    private val REFLECTIVE_IDS = setOf(79, 174, 42, 41, 57, 133, 22, 152)

    private const val NO_SPRITE = 0

    /** Blocks whose tinted faces want a specific colour rather than their material's default. */
    private val TINTS: Map<Int, Int> = mapOf(
        2 to GRASS_TINT,
        18 to FOLIAGE_TINT,
        161 to FOLIAGE_TINT,
        31 to GRASS_TINT,
        106 to FOLIAGE_TINT,
        175 to GRASS_TINT,
        WATER_STILL to WATER_TINT,
        WATER_FLOWING to WATER_TINT,
    )
}

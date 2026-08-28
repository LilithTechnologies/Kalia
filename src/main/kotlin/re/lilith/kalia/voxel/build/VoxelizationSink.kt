package re.lilith.kalia.voxel.build

import dev.rdh.argentum.impl.render.terrain.VoxelizationHook
import net.minecraft.block.BlockState
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.VoxelFormat
import re.lilith.kalia.voxel.VoxelWorld
import re.lilith.kalia.voxel.render.SvoRenderer

/**
 * Bridges the terrain mesher's per-block walk into [VoxelWorld].
 *
 * A [VoxelBrickBuilder] is kept per chunk build thread, so voxelising a section costs one table
 * lookup and one bit set per solid block on a thread that was already touching that block. Nothing
 * here allocates once the thread locals have warmed up, except the word array handed to the queue,
 * and even that comes out of a recycling pool.
 */
object VoxelizationSink : VoxelizationHook.Provider {
    private var installed = false

    private val builders = ThreadLocal.withInitial { VoxelBrickBuilder() }

    fun install() {
        if (installed) {
            return
        }
        installed = true
        VoxelizationHook.install(this)
    }

    override fun begin(originX: Int, originY: Int, originZ: Int): VoxelizationHook.SectionWriter? {
        if (!SvoSettings.voxelising) {
            return null
        }
        val sectionX = originX shr 4
        val sectionY = originY shr 4
        val sectionZ = originZ shr 4
        val builder = builders.get()
        builder.reset()
        return Writer(builder, sectionX, sectionY, sectionZ)
    }

    override fun empty(sectionX: Int, sectionY: Int, sectionZ: Int) {
        if (!SvoSettings.voxelising) {
            return
        }
        VoxelWorld.offerEmpty(sectionX, sectionY, sectionZ)
    }

    override fun reset() {
        VoxelWorld.clear()
        SvoRenderer.invalidateHistory()
    }

    override fun meshingDisabled(): Boolean = SvoSettings.enabled && SvoSettings.skipMeshing

    private class Writer(
        private val builder: VoxelBrickBuilder,
        private val sectionX: Int,
        private val sectionY: Int,
        private val sectionZ: Int,
    ) : VoxelizationHook.SectionWriter {
        override fun voxel(localX: Int, localY: Int, localZ: Int, state: BlockState, packedLight: Int) {
            val entry = VoxelMaterials.of(state)
            if (entry == VoxelMaterials.EMPTY) {
                return
            }
            builder.put(localX, localY, localZ, entry, packedLight)
        }

        override fun commit() {
            if (builder.solid == 0) {
                VoxelWorld.offerEmpty(sectionX, sectionY, sectionZ)
                return
            }
            val scratch = VoxelWorld.borrowScratch()
            val words = try {
                builder.build(scratch)
            } catch (failure: Throwable) {
                VoxelWorld.recycle(scratch)
                throw failure
            }
            if (words == 0) {
                VoxelWorld.recycle(scratch)
                VoxelWorld.offerEmpty(sectionX, sectionY, sectionZ)
                return
            }
            VoxelWorld.offer(
                sectionX = sectionX,
                sectionY = sectionY,
                sectionZ = sectionZ,
                words = scratch,
                wordCount = words,
                solidCount = builder.solid,
                color565 = builder.averageColor565,
            )
        }

        override fun abort() {
            builder.reset()
        }
    }

    /** Sanity check that the scratch pool is wide enough for the worst case brick. */
    init {
        val worst = VoxelFormat.PALETTE_OFFSET +
            VoxelFormat.MAX_PALETTE * VoxelFormat.PALETTE_ENTRY_WORDS +
            VoxelFormat.BRICK_VOXELS * 8 / 32
        check(VoxelFormat.MAX_BRICK_WORDS >= worst) {
            "Brick scratch holds ${VoxelFormat.MAX_BRICK_WORDS} words but the worst case needs $worst."
        }
    }
}

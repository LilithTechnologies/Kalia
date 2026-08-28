package re.lilith.kalia.voxel

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import re.lilith.kalia.voxel.pool.BrickArena
import re.lilith.kalia.voxel.pool.NodeArena
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the voxel representation of the loaded world: the brick arena, the node arena, the octree
 * on top of them, and the bookkeeping that keeps all three in step with chunk loading.
 *
 * Chunk build workers hand finished bricks in through [offer], which is the only entry point they
 * touch. Everything that mutates the tree runs on whichever thread calls [tick], with a per-frame
 * budget, so the structure itself never needs a lock.
 */
object VoxelWorld {
    private val recycled = ConcurrentLinkedQueue<IntArray>()
    private val incoming = ConcurrentLinkedQueue<PendingBrick>()
    private val pendingRemovals = ConcurrentLinkedQueue<Long>()

    private var brickArena = BrickArena(
        initialWords = SvoSettings.INITIAL_BRICK_WORDS,
        maximumWords = SvoSettings.brickBudgetWords,
    )
    private var nodeArena = NodeArena(
        initialNodes = SvoSettings.INITIAL_NODES,
        maximumNodes = SvoSettings.nodeBudget,
    )

    /** Section position, packed by [packSection], to `offset shl 32 or wordCount`. */
    private val sections = Long2LongOpenHashMap().apply { defaultReturnValue(ABSENT) }

    private var octree = VoxelOctree(SvoSettings.levels, nodeArena)

    /** Brick coordinate of the octree's minimum corner. */
    var originBrickX: Int = Int.MIN_VALUE
        private set
    var originBrickY: Int = 0
        private set
    var originBrickZ: Int = Int.MIN_VALUE
        private set

    private var anchored = false

    private val evictionScratch = LongArrayList()
    private var evictionCountdown = 0

    /** Bumped whenever the origin moves, so downstream caches know their world-space maths changed. */
    var anchorGeneration: Int = 0
        private set

    private val droppedBricks = AtomicLong()

    val levels: Int get() = octree.levels
    val span: Int get() = octree.span
    val rootNode: Int get() = octree.root
    val liveSections: Int get() = sections.size
    val nodes: NodeArena get() = nodeArena
    val bricks: BrickArena get() = brickArena
    val queuedBricks: Int get() = incoming.size
    val dropped: Long get() = droppedBricks.get()

    // -- producer side (chunk build workers) -----------------------------------------------------

    /**
     * Borrows scratch for a brick. Returned arrays are recycled, so a worker should always release
     * one through [offer] or [recycle] rather than dropping it on the floor.
     */
    fun borrowScratch(): IntArray = recycled.poll() ?: IntArray(VoxelFormat.MAX_BRICK_WORDS)

    fun recycle(scratch: IntArray) {
        if (recycled.size < RECYCLE_LIMIT) {
            recycled.add(scratch)
        }
    }

    /**
     * Queues a finished brick for insertion. Ownership of [words] transfers to the queue.
     */
    fun offer(sectionX: Int, sectionY: Int, sectionZ: Int, words: IntArray, wordCount: Int, solidCount: Int, color565: Int) {
        if (incoming.size >= QUEUE_LIMIT) {
            droppedBricks.incrementAndGet()
            recycle(words)
            return
        }
        incoming.add(
            PendingBrick(
                key = packSection(sectionX, sectionY, sectionZ),
                words = words,
                wordCount = wordCount,
                solidCount = solidCount,
                color565 = color565,
            ),
        )
    }

    /** Queues an empty section, which drops whatever brick used to be there. */
    fun offerEmpty(sectionX: Int, sectionY: Int, sectionZ: Int) {
        pendingRemovals.add(packSection(sectionX, sectionY, sectionZ))
    }

    // -- consumer side (render/main thread) ------------------------------------------------------

    /**
     * Applies queued work and keeps the octree anchored near the camera.
     *
     * @return true when anything changed and the GPU mirrors need re-uploading.
     */
    fun tick(cameraX: Double, cameraY: Double, cameraZ: Double): Boolean {
        var changed = reanchor(cameraX, cameraY, cameraZ)

        var budget = SvoSettings.uploadsPerFrame
        while (budget > 0) {
            val key = pendingRemovals.poll() ?: break
            if (drop(key)) {
                changed = true
            }
            budget--
        }

        while (budget > 0) {
            val pending = incoming.poll() ?: break
            budget--
            if (apply(pending)) {
                changed = true
            }
            recycle(pending.words)
        }

        if (evict(cameraX, cameraZ)) {
            changed = true
        }
        return changed
    }

    fun clear() {
        incoming.clear()
        pendingRemovals.clear()
        sections.clear()
        octree.clear()
        brickArena.clear()
        anchored = false
        originBrickX = Int.MIN_VALUE
        originBrickZ = Int.MIN_VALUE
        anchorGeneration++
    }

    /** Rebuilds both arenas at the sizes the settings currently ask for. */
    fun reconfigure() {
        brickArena = BrickArena(SvoSettings.INITIAL_BRICK_WORDS, SvoSettings.brickBudgetWords)
        nodeArena = NodeArena(SvoSettings.INITIAL_NODES, SvoSettings.nodeBudget)
        octree = VoxelOctree(SvoSettings.levels, nodeArena)
        sections.clear()
        incoming.clear()
        pendingRemovals.clear()
        anchored = false
        originBrickX = Int.MIN_VALUE
        originBrickZ = Int.MIN_VALUE
        anchorGeneration++
    }

    // -- internals -------------------------------------------------------------------------------

    private fun apply(pending: PendingBrick): Boolean {
        val key = pending.key
        if (pending.wordCount == 0 || pending.solidCount == 0) {
            return drop(key)
        }

        val brickX = unpackX(key) - originBrickX
        val brickY = unpackY(key) - originBrickY
        val brickZ = unpackZ(key) - originBrickZ
        if (!anchored || !octree.contains(brickX, brickY, brickZ)) {
            return false
        }

        val existing = sections.get(key)
        if (existing != ABSENT) {
            brickArena.free(offsetOf(existing), wordsOf(existing))
            sections.remove(key)
        }

        val offset = brickArena.allocate(pending.wordCount)
        if (offset == BrickArena.NO_OFFSET) {
            droppedBricks.incrementAndGet()
            if (existing != ABSENT) {
                octree.remove(brickX, brickY, brickZ)
            }
            return existing != ABSENT
        }
        brickArena.write(offset, pending.words, pending.wordCount)

        if (!octree.insert(brickX, brickY, brickZ, offset, pending.solidCount, pending.color565)) {
            brickArena.free(offset, pending.wordCount)
            droppedBricks.incrementAndGet()
            return existing != ABSENT
        }
        sections.put(key, pack(offset, pending.wordCount, pending.color565))
        return true
    }

    private fun drop(key: Long): Boolean {
        val existing = sections.remove(key)
        if (existing == ABSENT) {
            return false
        }
        brickArena.free(offsetOf(existing), wordsOf(existing))
        if (anchored) {
            octree.remove(unpackX(key) - originBrickX, unpackY(key) - originBrickY, unpackZ(key) - originBrickZ)
        }
        return true
    }

    /**
     * Keeps the root centred on the camera. The origin snaps to a quarter of the root span, so the
     * camera is always at least a quarter-span from every face and re-anchoring only happens after
     * the player has travelled that far, which for the default depth is 512 blocks.
     */
    private fun reanchor(cameraX: Double, cameraY: Double, cameraZ: Double): Boolean {
        val quarter = (octree.span / 4).coerceAtLeast(1)
        val half = octree.span / 2
        val camBrickX = Math.floorDiv(Math.floor(cameraX).toInt(), VoxelFormat.BRICK_EDGE)
        val camBrickZ = Math.floorDiv(Math.floor(cameraZ).toInt(), VoxelFormat.BRICK_EDGE)

        val targetX = Math.floorDiv(camBrickX - half, quarter) * quarter
        val targetZ = Math.floorDiv(camBrickZ - half, quarter) * quarter
        // Vanilla worlds are 256 blocks tall, which the shallowest supported root already covers, so
        // the vertical origin can stay pinned at the bottom of the world.
        val targetY = 0

        if (anchored && targetX == originBrickX && targetY == originBrickY && targetZ == originBrickZ) {
            return false
        }

        originBrickX = targetX
        originBrickY = targetY
        originBrickZ = targetZ
        anchored = true
        anchorGeneration++

        // Bricks keep their storage; only the tree above them is rebuilt.
        octree.clear()
        val stale = LongArrayList()
        val iterator = sections.long2LongEntrySet().fastIterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.longKey
            val value = entry.longValue
            val brickX = unpackX(key) - originBrickX
            val brickY = unpackY(key) - originBrickY
            val brickZ = unpackZ(key) - originBrickZ
            if (!octree.contains(brickX, brickY, brickZ) ||
                !octree.insert(brickX, brickY, brickZ, offsetOf(value), wordsOf(value), colorOf(value))
            ) {
                stale.add(key)
            }
        }
        for (index in stale.indices) {
            val key = stale.getLong(index)
            val value = sections.remove(key)
            if (value != ABSENT) {
                brickArena.free(offsetOf(value), wordsOf(value))
            }
        }
        return true
    }

    /**
     * Retires sections the player has walked away from. A section only leaves range after the
     * camera has moved a whole chunk, so sweeping every few frames is far cheaper than trying to
     * keep a distance-ordered structure up to date, and just as timely.
     */
    private fun evict(cameraX: Double, cameraZ: Double): Boolean {
        if (evictionCountdown-- > 0 || sections.isEmpty()) {
            return false
        }
        evictionCountdown = EVICTION_INTERVAL

        val limit = SvoSettings.voxelDistanceChunks
        val camChunkX = Math.floorDiv(Math.floor(cameraX).toInt(), 16)
        val camChunkZ = Math.floorDiv(Math.floor(cameraZ).toInt(), 16)

        evictionScratch.clear()
        val iterator = sections.keys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.nextLong()
            val dx = Math.abs(unpackX(key) - camChunkX)
            val dz = Math.abs(unpackZ(key) - camChunkZ)
            if (Math.max(dx, dz) > limit) {
                evictionScratch.add(key)
            }
        }

        if (evictionScratch.isEmpty) {
            return false
        }
        for (slot in 0 until evictionScratch.size) {
            drop(evictionScratch.getLong(slot))
        }
        return true
    }

    private class PendingBrick(
        val key: Long,
        val words: IntArray,
        val wordCount: Int,
        val solidCount: Int,
        val color565: Int,
    )

    // -- packing ---------------------------------------------------------------------------------

    private const val ABSENT = -1L

    private const val QUEUE_LIMIT = 4096
    private const val RECYCLE_LIMIT = 64

    /** Frames between eviction sweeps. */
    private const val EVICTION_INTERVAL = 20

    fun packSection(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFL) shl 42) or ((z.toLong() and 0x3FFFFFL) shl 20) or (y.toLong() and 0xFFFFL)

    private fun unpackX(key: Long): Int = (key shr 42).toInt()

    private fun unpackZ(key: Long): Int = ((key shl 22) shr 42).toInt()

    private fun unpackY(key: Long): Int = (key and 0xFFFFL).toInt()

    private fun pack(offset: Int, words: Int, color565: Int): Long =
        (offset.toLong() shl 32) or ((words.toLong() and 0xFFFFL) shl 16) or (color565.toLong() and 0xFFFFL)

    private fun offsetOf(value: Long): Int = (value ushr 32).toInt()

    private fun wordsOf(value: Long): Int = ((value ushr 16) and 0xFFFFL).toInt()

    private fun colorOf(value: Long): Int = (value and 0xFFFFL).toInt()
}

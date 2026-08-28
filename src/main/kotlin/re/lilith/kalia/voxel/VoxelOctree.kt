package re.lilith.kalia.voxel

import re.lilith.kalia.voxel.pool.NodeArena

/**
 * The sparse voxel octree itself: a pointer-based tree whose leaves are 16^3 bricks.
 *
 * Nodes live in a flat `uvec2` arena. An internal node stores its existing children as one
 * contiguous run, so descending costs a single population count instead of eight pointer slots,
 * and an empty region of the world costs exactly nothing. Both node kinds carry a 16-bit RGB565
 * average of their subtree in the upper half of a word, which is what the tracer shades with once
 * a node shrinks below a pixel.
 *
 * Coordinates handed to this class are *brick* coordinates relative to the tree origin, in
 * `0 until (1 shl levels)`. Anchoring that origin to the camera is [VoxelWorld]'s job.
 */
class VoxelOctree(val levels: Int, val arena: NodeArena) {
    init {
        require(levels in 1..VoxelFormat.MAX_LEVELS) { "Octree depth $levels is out of range." }
    }

    /** Bricks along one axis of the root. */
    val span: Int = 1 shl levels

    private val pathNode = IntArray(VoxelFormat.MAX_LEVELS + 1)
    private val pathSlot = IntArray(VoxelFormat.MAX_LEVELS + 1)

    var root: Int = allocateRoot()
        private set

    var leafCount: Int = 0
        private set

    private fun allocateRoot(): Int {
        val node = arena.allocateSingle()
        check(node != NodeArena.NO_NODE) { "The octree arena is too small to hold even a root node." }
        arena.setNode(node, 0, 0)
        return node
    }

    fun contains(brickX: Int, brickY: Int, brickZ: Int): Boolean =
        (brickX or brickY or brickZ) >= 0 && brickX < span && brickY < span && brickZ < span

    /**
     * Attaches a brick as the leaf covering the given brick coordinate, creating the internal
     * nodes above it as needed.
     *
     * @return false when the node arena ran out of room, in which case the tree is unchanged.
     */
    fun insert(brickX: Int, brickY: Int, brickZ: Int, brickOffset: Int, meta: Int, color565: Int): Boolean {
        if (!contains(brickX, brickY, brickZ)) {
            return false
        }

        var node = root
        var depth = 0
        for (level in levels downTo 1) {
            val shift = level - 1
            val slot = VoxelFormat.octantSlot(brickX ushr shift, brickY ushr shift, brickZ ushr shift)
            pathNode[depth] = node
            pathSlot[depth] = slot
            depth++

            val leafLevel = level == 1
            val masks = arena.masks(node)
            val childMask = VoxelFormat.childMask(masks)
            val rank = VoxelFormat.childRank(childMask, slot)

            if (childMask and (1 shl slot) != 0) {
                node = arena.pointer(node) + rank
                if (leafLevel) {
                    writeLeaf(node, brickOffset, meta, color565)
                    refresh(depth - 1)
                    return true
                }
                continue
            }

            val child = grow(node, masks, slot, rank, internal = !leafLevel) ?: return false
            if (leafLevel) {
                writeLeaf(child, brickOffset, meta, color565)
                leafCount++
                refresh(depth - 1)
                return true
            }
            arena.setNode(child, 0, 0)
            node = child
        }
        return false
    }

    /**
     * Detaches the leaf at the given brick coordinate and prunes every ancestor it emptied.
     *
     * @return true when a leaf was actually there.
     */
    fun remove(brickX: Int, brickY: Int, brickZ: Int): Boolean {
        if (!contains(brickX, brickY, brickZ)) {
            return false
        }

        var node = root
        var depth = 0
        for (level in levels downTo 1) {
            val shift = level - 1
            val slot = VoxelFormat.octantSlot(brickX ushr shift, brickY ushr shift, brickZ ushr shift)
            val masks = arena.masks(node)
            val childMask = VoxelFormat.childMask(masks)
            if (childMask and (1 shl slot) == 0) {
                return false
            }
            pathNode[depth] = node
            pathSlot[depth] = slot
            depth++
            node = arena.pointer(node) + VoxelFormat.childRank(childMask, slot)
        }

        leafCount--

        var index = depth - 1
        while (index >= 0) {
            val parent = pathNode[index]
            detach(parent, pathSlot[index])
            if (parent == root || VoxelFormat.childMask(arena.masks(parent)) != 0) {
                break
            }
            index--
        }
        refresh(if (index > 0) index - 1 else 0)
        return true
    }

    /** Empties the tree, keeping the arena so the GPU buffers do not have to be rebuilt. */
    fun clear() {
        arena.clear()
        root = allocateRoot()
        leafCount = 0
    }

    /**
     * Looks up the brick stored at a brick coordinate.
     *
     * @return the brick word offset, or [NodeArena.NO_NODE] when nothing is stored there.
     */
    fun leafBrick(brickX: Int, brickY: Int, brickZ: Int): Int {
        if (!contains(brickX, brickY, brickZ)) {
            return NodeArena.NO_NODE
        }
        var node = root
        for (level in levels downTo 1) {
            val shift = level - 1
            val slot = VoxelFormat.octantSlot(brickX ushr shift, brickY ushr shift, brickZ ushr shift)
            val childMask = VoxelFormat.childMask(arena.masks(node))
            if (childMask and (1 shl slot) == 0) {
                return NodeArena.NO_NODE
            }
            node = arena.pointer(node) + VoxelFormat.childRank(childMask, slot)
        }
        return arena.masks(node)
    }

    // -- internals -------------------------------------------------------------------------------

    private fun writeLeaf(node: Int, brickOffset: Int, meta: Int, color565: Int) {
        arena.setNode(node, brickOffset, (meta and 0xFFFF) or (color565 shl 16))
    }

    /**
     * Opens a slot for a new child, shifting the ones after it up within the node's own run.
     *
     * @return the index of the freshly opened child slot, or null when the arena is exhausted.
     */
    private fun grow(node: Int, masks: Int, slot: Int, rank: Int, internal: Boolean): Int? {
        val childMask = VoxelFormat.childMask(masks)
        val internalMask = VoxelFormat.internalMask(masks)
        val existing = Integer.bitCount(childMask)

        var pointer = arena.pointer(node)
        if (existing == 0) {
            pointer = arena.allocateRun()
            if (pointer == NodeArena.NO_NODE) {
                return null
            }
        } else {
            // Runs are always eight slots, so there is always room; the tail just moves up one.
            arena.copyRun(pointer + rank + 1, pointer + rank, existing - rank)
        }

        val nextChildMask = childMask or (1 shl slot)
        val nextInternalMask = if (internal) {
            internalMask or (1 shl slot)
        } else {
            internalMask and (1 shl slot).inv()
        }
        arena.setNode(
            node,
            VoxelFormat.nodeMasks(nextChildMask, nextInternalMask) or (masks and COLOR_MASK),
            pointer,
        )
        return pointer + rank
    }

    /**
     * Drops one child from a node, shifting the ones after it down. The run itself only goes back
     * to the arena once the node has no children left, so removal can never fail for want of memory.
     */
    private fun detach(node: Int, slot: Int) {
        val masks = arena.masks(node)
        val childMask = VoxelFormat.childMask(masks)
        if (childMask and (1 shl slot) == 0) {
            return
        }
        val internalMask = VoxelFormat.internalMask(masks)
        val remaining = Integer.bitCount(childMask) - 1
        val pointer = arena.pointer(node)
        val rank = VoxelFormat.childRank(childMask, slot)

        if (remaining == 0) {
            arena.freeRun(pointer)
            arena.setNode(node, masks and COLOR_MASK, 0)
            return
        }

        arena.copyRun(pointer + rank, pointer + rank + 1, remaining - rank)
        arena.setNode(
            node,
            VoxelFormat.nodeMasks(
                childMask and (1 shl slot).inv(),
                internalMask and (1 shl slot).inv(),
            ) or (masks and COLOR_MASK),
            pointer,
        )
    }

    /** Recomputes the average colour of every node on the recorded path, innermost first. */
    private fun refresh(lastDepth: Int) {
        for (depth in lastDepth downTo 0) {
            val node = pathNode[depth]
            val masks = arena.masks(node)
            val pointer = arena.pointer(node)
            val childMask = VoxelFormat.childMask(masks)
            val internalMask = VoxelFormat.internalMask(masks)
            val count = Integer.bitCount(childMask)
            if (count == 0) {
                arena.setNode(node, masks and COLOR_MASK.inv(), pointer)
                continue
            }

            var red = 0
            var green = 0
            var blue = 0
            var rank = 0
            for (slot in 0 until 8) {
                if (childMask and (1 shl slot) == 0) {
                    continue
                }
                val child = pointer + rank
                val packed = if (internalMask and (1 shl slot) != 0) {
                    arena.masks(child) ushr 16
                } else {
                    arena.pointer(child) ushr 16
                }
                red += (packed ushr 11) and 0x1F
                green += (packed ushr 5) and 0x3F
                blue += packed and 0x1F
                rank++
            }
            val averaged = (((red / count) and 0x1F) shl 11) or
                (((green / count) and 0x3F) shl 5) or
                ((blue / count) and 0x1F)
            arena.setNode(node, (masks and COLOR_MASK.inv()) or (averaged shl 16), pointer)
        }
    }

    companion object {
        private const val COLOR_MASK = 0xFFFF0000.toInt()

        fun pack565(red: Int, green: Int, blue: Int): Int =
            (((red shr 3) and 0x1F) shl 11) or (((green shr 2) and 0x3F) shl 5) or ((blue shr 3) and 0x1F)
    }
}

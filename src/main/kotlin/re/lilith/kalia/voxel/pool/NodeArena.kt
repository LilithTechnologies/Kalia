package re.lilith.kalia.voxel.pool

import it.unimi.dsi.fastutil.ints.IntArrayList
import re.lilith.kalia.voxel.VoxelFormat

/**
 * Allocates the octree's child runs.
 *
 * Every internal node gets a full eight-slot run whether it uses them or not. Variable-length runs
 * sound thriftier, but a world where blocks are constantly placed and broken churns nodes between
 * lengths all day, and size-classed free lists fragment badly under that: a run freed as four slots
 * cannot satisfy the request for five that follows. A single uniform size makes the free list exact,
 * makes growing and shrinking a node a memmove within its own run instead of a reallocation, and
 * costs only the slots a sparse node leaves empty. For a full render distance that overhead is
 * under a megabyte.
 */
class NodeArena(initialNodes: Int, maximumNodes: Int) {
    val storage = PagedWords(
        "kalia/svo-nodes",
        initialNodes * VoxelFormat.NODE_WORDS,
        maximumNodes * VoxelFormat.NODE_WORDS,
    )

    private val recycled = IntArrayList()

    /** Runs handed out and not yet returned. */
    var liveRuns: Int = 0
        private set

    val usedNodes: Int get() = storage.used / VoxelFormat.NODE_WORDS

    val capacityNodes: Int get() = storage.capacity / VoxelFormat.NODE_WORDS

    /** Slots sitting in the free list, reusable without growing. */
    val freeNodes: Int get() = recycled.size * RUN_SLOTS

    /**
     * Reserves one slot that is never freed. Only the root needs this.
     *
     * @return the slot index, or [NO_NODE] when the arena is full.
     */
    fun allocateSingle(): Int = bump(1)

    /**
     * Reserves a run of [RUN_SLOTS] adjacent slots.
     *
     * @return the index of the first slot, or [NO_NODE] when the arena is full.
     */
    fun allocateRun(): Int {
        if (!recycled.isEmpty) {
            liveRuns++
            return recycled.popInt()
        }
        val index = bump(RUN_SLOTS)
        if (index != NO_NODE) {
            liveRuns++
        }
        return index
    }

    fun freeRun(index: Int) {
        if (index == NO_NODE) {
            return
        }
        recycled.add(index)
        liveRuns--
    }

    fun masks(node: Int): Int = storage.get(node * VoxelFormat.NODE_WORDS)

    fun pointer(node: Int): Int = storage.get(node * VoxelFormat.NODE_WORDS + 1)

    fun setNode(node: Int, masks: Int, pointer: Int) {
        val word = node * VoxelFormat.NODE_WORDS
        storage.set(word, masks)
        storage.set(word + 1, pointer)
    }

    /** Moves [length] adjacent slots. Source and destination may overlap. */
    fun copyRun(destination: Int, source: Int, length: Int) {
        storage.copyWithin(
            destination * VoxelFormat.NODE_WORDS,
            source * VoxelFormat.NODE_WORDS,
            length * VoxelFormat.NODE_WORDS,
        )
    }

    fun clearNodes(index: Int, length: Int) {
        storage.fill(index * VoxelFormat.NODE_WORDS, length * VoxelFormat.NODE_WORDS, 0)
    }

    fun clear() {
        recycled.clear()
        liveRuns = 0
        storage.reset()
    }

    private fun bump(slots: Int): Int {
        val words = slots * VoxelFormat.NODE_WORDS
        val offset = storage.used
        if (!storage.ensureCapacity(offset + words)) {
            return NO_NODE
        }
        storage.used = offset + words
        return offset / VoxelFormat.NODE_WORDS
    }

    companion object {
        const val NO_NODE = -1

        /** An octree node has at most eight children, so a run never needs more than eight slots. */
        const val RUN_SLOTS = 8
    }
}

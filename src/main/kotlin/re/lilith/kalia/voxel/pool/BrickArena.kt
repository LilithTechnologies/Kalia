package re.lilith.kalia.voxel.pool

import it.unimi.dsi.fastutil.ints.IntArrayList
import re.lilith.kalia.voxel.VoxelFormat

/**
 * Suballocates variable-length brick records out of one big storage buffer.
 *
 * Brick sizes are quantised to [QUANTUM] words and each quantum gets its own LIFO free list, so a
 * section that is rebuilt (a block placed, a leaf decaying) almost always lands back in the slot it
 * just vacated. That keeps fragmentation flat over a long session without any compaction pass.
 */
class BrickArena(initialWords: Int, maximumWords: Int) {
    val storage = PagedWords("kalia/svo-bricks", initialWords, maximumWords)

    private val classes = Array(CLASS_COUNT) { IntArrayList() }

    /** Words sitting in free lists, i.e. reclaimable without growing. */
    var freeWords: Int = 0
        private set

    var liveBricks: Int = 0
        private set

    val usedWords: Int get() = storage.used

    val capacityWords: Int get() = storage.capacity

    /**
     * Reserves space for a record of [words] words.
     *
     * @return the word offset, or [NO_OFFSET] when the arena is at its ceiling.
     */
    fun allocate(words: Int): Int {
        require(words in 1..VoxelFormat.MAX_BRICK_WORDS) { "Brick of $words words is out of range." }
        val sizeClass = classOf(words)
        val quantised = sizeClass * QUANTUM

        val recycled = classes[sizeClass]
        if (!recycled.isEmpty) {
            val offset = recycled.popInt()
            freeWords -= quantised
            liveBricks++
            return offset
        }

        val offset = storage.used
        if (!storage.ensureCapacity(offset + quantised)) {
            return NO_OFFSET
        }
        storage.used = offset + quantised
        liveBricks++
        return offset
    }

    fun free(offset: Int, words: Int) {
        if (offset == NO_OFFSET) {
            return
        }
        val sizeClass = classOf(words)
        classes[sizeClass].add(offset)
        freeWords += sizeClass * QUANTUM
        liveBricks--
    }

    fun write(offset: Int, source: IntArray, length: Int) {
        storage.copyIn(offset, source, 0, length)
    }

    fun clear() {
        classes.forEach(IntArrayList::clear)
        freeWords = 0
        liveBricks = 0
        storage.reset()
    }

    private fun classOf(words: Int): Int = (words + QUANTUM - 1) / QUANTUM

    companion object {
        const val NO_OFFSET = -1

        /** 64 words is 256 bytes: fine enough that quantisation waste stays under 2% of a brick. */
        const val QUANTUM = 64

        private val CLASS_COUNT = (VoxelFormat.MAX_BRICK_WORDS + QUANTUM - 1) / QUANTUM + 1
    }
}

package re.lilith.kalia.voxel.pool

/**
 * A growable `uint[]` mirror of a GPU storage buffer, with page-granular dirty tracking so that
 * only the words that actually changed are re-uploaded.
 *
 * Every write goes through here, which keeps the "what needs uploading" bookkeeping in one place
 * and lets the allocators above stay ignorant of the GPU entirely.
 */
class PagedWords(
    val label: String,
    initialWords: Int,
    /** Hard ceiling. Growth stops here and allocations start failing rather than exhausting VRAM. */
    val maximumWords: Int,
) {
    /** Words per dirty page. 1024 words is 4 KiB, which matches a typical transfer granularity. */
    private val pageWords = PAGE_WORDS

    var words: IntArray = IntArray(align(initialWords.coerceAtLeast(pageWords)))
        private set

    /** Bumped whenever [words] is reallocated, so the GPU side knows to rebuild its buffer. */
    var generation: Int = 0
        private set

    private var dirty = java.util.BitSet(words.size / pageWords)

    /** Total words handed out, used for reporting and for the growth heuristic. */
    var used: Int = 0
        internal set

    val capacity: Int get() = words.size

    fun markAllDirty() {
        dirty.set(0, words.size / pageWords)
    }

    fun clearDirty() {
        dirty.clear()
    }

    fun hasDirty(): Boolean = !dirty.isEmpty

    /**
     * Visits every maximal run of dirty pages as a `[firstWord, wordCount)` pair.
     */
    inline fun forEachDirtyRange(body: (firstWord: Int, wordCount: Int) -> Unit) {
        var page = nextDirtyPage(0)
        while (page >= 0) {
            var end = page + 1
            while (isPageDirty(end)) {
                end++
            }
            body(page * PAGE_WORDS, (end - page) * PAGE_WORDS)
            page = nextDirtyPage(end + 1)
        }
    }

    @PublishedApi
    internal fun nextDirtyPage(from: Int): Int = dirty.nextSetBit(from)

    @PublishedApi
    internal fun isPageDirty(page: Int): Boolean = dirty.get(page)

    /**
     * Marks a word range as uploaded. Only whole pages are cleared, so a partial upload leaves the
     * straddled page dirty and it is simply copied again next frame.
     */
    fun clearDirtyRange(firstWord: Int, wordCount: Int) {
        if (wordCount <= 0) {
            return
        }
        val first = (firstWord + pageWords - 1) / pageWords
        val last = (firstWord + wordCount) / pageWords
        if (last > first) {
            dirty.clear(first, last)
        }
    }

    fun markDirty(firstWord: Int, wordCount: Int) {
        if (wordCount <= 0) {
            return
        }
        val first = firstWord / pageWords
        val last = (firstWord + wordCount - 1) / pageWords
        dirty.set(first, last + 1)
    }

    fun set(index: Int, value: Int) {
        words[index] = value
        dirty.set(index / pageWords)
    }

    fun get(index: Int): Int = words[index]

    fun copyIn(destination: Int, source: IntArray, sourceOffset: Int, length: Int) {
        if (length <= 0) {
            return
        }
        System.arraycopy(source, sourceOffset, words, destination, length)
        markDirty(destination, length)
    }

    fun copyWithin(destination: Int, source: Int, length: Int) {
        if (length <= 0) {
            return
        }
        System.arraycopy(words, source, words, destination, length)
        markDirty(destination, length)
    }

    fun fill(destination: Int, length: Int, value: Int) {
        if (length <= 0) {
            return
        }
        java.util.Arrays.fill(words, destination, destination + length, value)
        markDirty(destination, length)
    }

    /**
     * Grows the mirror so that [requiredWords] fit, doubling until it does.
     *
     * @return false when the ceiling would be exceeded, in which case nothing changed.
     */
    fun ensureCapacity(requiredWords: Int): Boolean {
        if (requiredWords <= words.size) {
            return true
        }
        if (requiredWords > maximumWords) {
            return false
        }
        var target = words.size
        while (target < requiredWords) {
            target = target shl 1
            if (target <= 0 || target > maximumWords) {
                target = maximumWords
                break
            }
        }
        target = align(target)
        val grown = IntArray(target)
        System.arraycopy(words, 0, grown, 0, words.size)
        words = grown
        generation++

        val resized = java.util.BitSet(target / pageWords)
        resized.or(dirty)
        dirty = resized
        // A fresh buffer starts out with nothing on the GPU, so everything counts as dirty.
        markAllDirty()
        return true
    }

    fun reset() {
        used = 0
        java.util.Arrays.fill(words, 0)
        markAllDirty()
    }

    private fun align(value: Int): Int = ((value + pageWords - 1) / pageWords) * pageWords

    companion object {
        const val PAGE_WORDS = 1024
    }
}

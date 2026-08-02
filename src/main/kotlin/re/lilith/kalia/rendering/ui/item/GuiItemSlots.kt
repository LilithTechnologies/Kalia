package re.lilith.kalia.rendering.ui.item

import java.util.*

class GuiItemSlots(
    val slotSize: Int,
    val atlasSize: Int,
) {
    val stride = (atlasSize / slotSize).coerceAtLeast(1)
    val capacity = stride * stride

    private val keys = arrayOfNulls<Any>(capacity)
    private val lastUsed = LongArray(capacity)
    private val animated = BooleanArray(capacity)
    private val sourceVersion = LongArray(capacity)

    private val index = HashMap<Any, Int>(capacity)

    private var occupied = 0

    val size: Int get() = occupied

    fun acquire(key: Any, frame: Long, currentSourceVersion: Long): Long {
        val existing = index[key]
        if (existing != null) {
            lastUsed[existing] = frame
            val stale = animated[existing] && sourceVersion[existing] != currentSourceVersion
            if (stale) {
                sourceVersion[existing] = currentSourceVersion
            }
            return pack(existing, stale)
        }

        val slot = if (occupied < capacity) occupied++ else evict()
        keys[slot]?.let(index::remove)
        keys[slot] = key
        lastUsed[slot] = frame
        animated[slot] = false
        sourceVersion[slot] = currentSourceVersion
        index[key] = slot
        return pack(slot, needsFill = true)
    }

    fun setAnimated(slot: Int, value: Boolean) {
        animated[slot] = value
    }

    fun forget(slot: Int) {
        animated[slot] = true
        sourceVersion[slot] = NEVER_MATCHED
    }

    fun slotX(slot: Int): Int = (slot % stride) * slotSize
    fun slotY(slot: Int): Int = (slot / stride) * slotSize

    fun slotU0(slot: Int): Float = slotX(slot).toFloat() / atlasSize
    fun slotV0(slot: Int): Float = slotY(slot).toFloat() / atlasSize

    fun slotU1(slot: Int): Float = (slotX(slot) + slotSize).toFloat() / atlasSize
    fun slotV1(slot: Int): Float = (slotY(slot) + slotSize).toFloat() / atlasSize

    fun clear() {
        Arrays.fill(keys, null)
        index.clear()
        occupied = 0
    }

    private fun evict(): Int {
        var oldest = 0
        var oldestFrame = Long.MAX_VALUE
        for (slot in 0 until capacity) {
            if (lastUsed[slot] < oldestFrame) {
                oldestFrame = lastUsed[slot]
                oldest = slot
            }
        }
        return oldest
    }

    companion object {
        private const val NEVER_MATCHED = Long.MIN_VALUE

        private fun pack(slot: Int, needsFill: Boolean): Long =
            slot.toLong() or (if (needsFill) FILL_BIT else 0L)

        private const val FILL_BIT = 1L shl 32

        fun slotOf(packed: Long): Int = (packed and 0xFFFFFFFFL).toInt()

        fun needsFill(packed: Long): Boolean = (packed and FILL_BIT) != 0L
    }
}

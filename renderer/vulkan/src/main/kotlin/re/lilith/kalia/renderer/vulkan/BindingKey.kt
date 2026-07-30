package re.lilith.kalia.renderer.vulkan

/**
 * Identifies a descriptor set by the resources bound into it
 */
internal class BindingKey {
    private var layout: Any? = null
    private var objects = arrayOfNulls<Any?>(INITIAL_SLOTS)
    private var ranges = LongArray(INITIAL_SLOTS)
    private var count = 0
    private var hash = 0

    fun begin(layout: Any?, bindingCount: Int) {
        this.layout = layout
        val slots = bindingCount * SLOTS_PER_BINDING
        if (objects.size < slots) {
            objects = arrayOfNulls(slots)
            ranges = LongArray(slots)
        }
        count = bindingCount
    }

    fun put(index: Int, primary: Any?, secondary: Any?, offset: Long, size: Long) {
        val slot = index * SLOTS_PER_BINDING
        objects[slot] = primary
        objects[slot + 1] = secondary
        ranges[slot] = offset
        ranges[slot + 1] = size
    }

    /** Freezes the hash. Must be called after the last [put] and before any lookup. */
    fun seal() {
        var result = System.identityHashCode(layout)
        for (slot in 0 until count * SLOTS_PER_BINDING) {
            result = result * 31 + System.identityHashCode(objects[slot])
            val range = ranges[slot]
            result = result * 31 + (range xor (range ushr 32)).toInt()
        }
        hash = result
    }

    fun copy(): BindingKey {
        val clone = BindingKey()
        clone.layout = layout
        clone.count = count
        val slots = count * SLOTS_PER_BINDING
        clone.objects = objects.copyOf(slots)
        clone.ranges = ranges.copyOf(slots)
        clone.hash = hash
        return clone
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BindingKey || hash != other.hash || layout !== other.layout || count != other.count) {
            return false
        }
        for (slot in 0 until count * SLOTS_PER_BINDING) {
            if (objects[slot] !== other.objects[slot] || ranges[slot] != other.ranges[slot]) {
                return false
            }
        }
        return true
    }

    private companion object {
        const val SLOTS_PER_BINDING = 2
        const val INITIAL_SLOTS = 8 * SLOTS_PER_BINDING
    }
}

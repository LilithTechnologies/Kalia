package re.lilith.kalia.renderer.vulkan

import java.util.IdentityHashMap

internal class VulkanTransferHazards {
    private val written = IdentityHashMap<Any, Ranges>()
    private val read = IdentityHashMap<Any, Boolean>()

    private class Ranges {
        val starts = ArrayList<Long>()
        val ends = ArrayList<Long>()
        var lowest = Long.MAX_VALUE
        var highest = Long.MIN_VALUE

        fun overlaps(start: Long, end: Long): Boolean {
            if (start >= highest || end <= lowest) {
                return false
            }
            for (index in starts.indices) {
                if (start < ends[index] && starts[index] < end) {
                    return true
                }
            }
            return false
        }

        fun add(start: Long, end: Long) {
            starts += start
            ends += end
            if (start < lowest) lowest = start
            if (end > highest) highest = end
        }
    }

    fun writeConflicts(target: Any, offset: Long, size: Long): Boolean {
        if (read.containsKey(target)) {
            return true
        }
        return written[target]?.overlaps(offset, offset + size) == true
    }

    fun copyConflicts(source: Any, destination: Any, writeOffset: Long, size: Long): Boolean =
        written.containsKey(source) || writeConflicts(destination, writeOffset, size)

    fun recordWrite(target: Any, offset: Long, size: Long) {
        written.getOrPut(target) { Ranges() }.add(offset, offset + size)
    }

    fun recordRead(source: Any) {
        read[source] = true
    }

    fun clear() {
        written.clear()
        read.clear()
    }
}

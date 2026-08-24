package re.lilith.kalia.frame

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

object FrameAllocations {
    private val bean: ThreadMXBean? = runCatching {
        (ManagementFactory.getThreadMXBean() as? ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
            ?.apply {
                if (!isThreadAllocatedMemoryEnabled) {
                    isThreadAllocatedMemoryEnabled = true
                }
            }
            ?.takeIf { it.isThreadAllocatedMemoryEnabled }
    }.getOrNull()

    private var mark = UNAVAILABLE
    private var average = 0.0

    val isSupported: Boolean get() = bean != null

    val bytesPerFrame: Double get() = average

    fun begin() {
        mark = sample()
    }

    fun end() {
        val start = mark
        if (start == UNAVAILABLE) {
            return
        }
        mark = UNAVAILABLE

        val current = sample()
        if (current == UNAVAILABLE) {
            return
        }

        average += ((current - start) - average) * SMOOTHING
    }

    private fun sample(): Long = bean?.currentThreadAllocatedBytes ?: UNAVAILABLE

    private const val UNAVAILABLE = -1L
    private const val SMOOTHING = 1.0 / 60.0
}

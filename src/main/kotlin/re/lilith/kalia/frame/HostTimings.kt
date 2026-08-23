package re.lilith.kalia.frame

object HostTimings {
    private var tickNanos = 0L
    private var displayNanos = 0L

    private var tickAverage = 0.0
    private var displayAverage = 0.0

    val tickMillis: Double get() = tickAverage / NANOS_PER_MILLI

    val displayMillis: Double get() = displayAverage / NANOS_PER_MILLI

    @JvmStatic
    fun addTick(nanos: Long) {
        tickNanos += nanos
    }

    @JvmStatic
    fun addDisplay(nanos: Long) {
        displayNanos += nanos
    }

    fun beginFrame() {
        tickAverage += (tickNanos - tickAverage) * SMOOTHING
        displayAverage += (displayNanos - displayAverage) * SMOOTHING
        tickNanos = 0L
        displayNanos = 0L
    }

    private const val SMOOTHING = 0.05
    private const val NANOS_PER_MILLI = 1_000_000.0
}

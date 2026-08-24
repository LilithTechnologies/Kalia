package re.lilith.kalia.frame.graph

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap

object EntityPoseStats {
    private val previous = Int2LongOpenHashMap().apply { defaultReturnValue(Long.MIN_VALUE) }
    private val seen = Int2LongOpenHashMap()

    @JvmField
    var entities = 0

    @JvmField
    var stable = 0

    private var entitiesAverage = 0.0
    private var stableAverage = 0.0

    val entitiesPerFrame: Int get() = entitiesAverage.toInt()

    val stablePercent: Int
        get() = if (entitiesAverage <= 0.0) 0 else (stableAverage * 100.0 / entitiesAverage).toInt()

    @JvmStatic
    fun observe(id: Int, signature: Long) {
        entities++
        if (previous.get(id) == signature) {
            stable++
        }
        seen.put(id, signature)
    }

    fun beginFrame() {
        entitiesAverage += (entities - entitiesAverage) * SMOOTHING
        stableAverage += (stable - stableAverage) * SMOOTHING
        entities = 0
        stable = 0
        previous.clear()
        previous.putAll(seen)
        seen.clear()
    }

    private const val SMOOTHING = 0.05
}

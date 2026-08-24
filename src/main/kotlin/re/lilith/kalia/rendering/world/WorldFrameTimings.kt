package re.lilith.kalia.rendering.world

import re.lilith.kalia.renderer.device.RenderStats

object WorldFrameTimings {
    const val EXTRACT = 0
    const val TERRAIN_PREPARE = 1
    const val SKY = 2
    const val CLOUDS = 3
    const val TERRAIN_SUBMIT = 4
    const val ENTITIES = 5
    const val OVERLAYS = 6
    const val PARTICLES = 7
    const val WEATHER_HAND = 8
    const val GUI_WALK = 9
    const val GRAPH_BUILD = 10
    const val DEVICE_RENDER = 11

    const val PART_REPLAY = 0
    const val PART_WORLD_PASS = 1
    const val PART_UI_PASS = 2
    const val PART_ATLAS_PASS = 3

    private val NAMES = arrayOf(
        "extract",
        "terrain prep",
        "sky",
        "clouds",
        "terrain",
        "entities",
        "overlays",
        "particles",
        "weather/hand",
        "gui walk",
        "graph build",
        "device render",
    )

    private val PART_NAMES = arrayOf("replay", "world pass", "ui pass", "atlas pass")

    private const val SMOOTHING = 1.0 / 60.0
    private const val NANOS_PER_MILLI = 1_000_000.0

    private const val FIRST_COLLECT_STAGE = EXTRACT
    private const val LAST_COLLECT_STAGE = GUI_WALK
    private const val FIRST_ENCODE_STAGE = GRAPH_BUILD
    private const val LAST_ENCODE_STAGE = DEVICE_RENDER

    private val average = DoubleArray(NAMES.size)
    private val partNanos = LongArray(PART_NAMES.size)
    private val partAverage = DoubleArray(PART_NAMES.size)
    private var gpuWaitAverage = 0.0
    private var mark = 0L

    val stageCount: Int get() = NAMES.size

    val partCount: Int get() = PART_NAMES.size

    fun stageName(stage: Int): String = NAMES[stage]

    fun stageMillis(stage: Int): Double = average[stage] / NANOS_PER_MILLI

    fun partName(part: Int): String = PART_NAMES[part]

    fun partMillis(part: Int): Double = partAverage[part] / NANOS_PER_MILLI

    val gpuWaitMillis: Double get() = gpuWaitAverage / NANOS_PER_MILLI

    val collectMillis: Double get() = sumStages(FIRST_COLLECT_STAGE, LAST_COLLECT_STAGE)

    val encodeMillis: Double
        get() = (sumStages(FIRST_ENCODE_STAGE, LAST_ENCODE_STAGE) - gpuWaitMillis).coerceAtLeast(0.0)

    val frameMillis: Double get() = collectMillis + encodeMillis

    private var wallMark = 0L
    private var wallAverage = 0.0
    private var insideMark = 0L
    private var insideAverage = 0.0

    val wallMillis: Double get() = wallAverage / NANOS_PER_MILLI

    val insideMillis: Double get() = insideAverage / NANOS_PER_MILLI

    val outsideMillis: Double get() = (wallMillis - insideMillis).coerceAtLeast(0.0)

    fun markWall() {
        val now = System.nanoTime()
        if (wallMark != 0L) {
            wallAverage += ((now - wallMark) - wallAverage) * SMOOTHING
        }
        wallMark = now
        insideMark = now
    }

    fun markWallEnd() {
        if (insideMark == 0L) {
            return
        }
        insideAverage += ((System.nanoTime() - insideMark) - insideAverage) * SMOOTHING
    }

    val collectShare: Double
        get() {
            val total = frameMillis
            return if (total > 0.0) collectMillis / total else 0.0
        }

    fun begin() {
        for (part in partNanos.indices) {
            partAverage[part] += (partNanos[part] - partAverage[part]) * SMOOTHING
        }
        partNanos.fill(0L)
        gpuWaitAverage += (RenderStats.gpuWaitNanos - gpuWaitAverage) * SMOOTHING
        mark = System.nanoTime()
    }

    fun addPart(part: Int, nanos: Long) {
        partNanos[part] += nanos
    }

    inline fun <T> part(index: Int, body: () -> T): T {
        val started = System.nanoTime()
        try {
            return body()
        } finally {
            addPart(index, System.nanoTime() - started)
        }
    }

    fun end(stage: Int) {
        val now = System.nanoTime()
        average[stage] += ((now - mark) - average[stage]) * SMOOTHING
        mark = now
    }

    private fun sumStages(first: Int, last: Int): Double {
        var total = 0.0
        for (stage in first..last) {
            total += average[stage]
        }
        return total / NANOS_PER_MILLI
    }
}

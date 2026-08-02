package re.lilith.kalia.rendering.world

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

    private const val SMOOTHING = 1.0 / 60.0

    private val average = DoubleArray(NAMES.size)
    private var mark = 0L

    const val PART_REPLAY = 0
    const val PART_WORLD_PASS = 1
    const val PART_UI_PASS = 2
    const val PART_ATLAS_PASS = 3

    private val PART_NAMES = arrayOf("replay", "world pass", "ui pass", "atlas pass")

    private val partNanos = LongArray(PART_NAMES.size)

    fun begin() {
        mark = System.nanoTime()
        partNanos.fill(0L)
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

    fun end(slot: Int) {
        val now = System.nanoTime()
        average[slot] += ((now - mark) - average[slot]) * SMOOTHING
        mark = now
    }
}

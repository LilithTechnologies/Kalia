package re.lilith.kalia.frame

import net.minecraft.client.MinecraftClient
import org.taumc.celeritas.impl.Celeritas

object FrameCounter {
    private val frameTimings = LongArray(SAMPLE_SIZE)

    private var frameIndex = 0
    private var lastFrameTime = System.nanoTime()
    private var isFilled = false

    private var currentFps = 0.0
    private var avgFps = 0.0
    private var avgFrameTime = 0.0

    private var frameCounter = 0
    private var avgFrameCounter = 0

    fun render() {
        val minecraft = MinecraftClient.getInstance()

        if (!Celeritas.CONFIG.fpsOverlay) return

        val currentTime = System.nanoTime()
        val deltaTime = currentTime - lastFrameTime
        lastFrameTime = currentTime

        frameTimings[frameIndex] = deltaTime
        frameIndex = (frameIndex + 1) % SAMPLE_SIZE
        if (frameIndex == 0) {
            isFilled = true
        }

        frameCounter++
        avgFrameCounter++

        if (frameCounter >= FPS_UPDATE_INTERVAL) {
            currentFps = MinecraftClient.getCurrentFps().toDouble()
            frameCounter = 0
        }

        if (avgFrameCounter >= AVG_UPDATE_INTERVAL) {
            avgFrameTime = this.avgFt
            avgFps = 1000000000.0 / avgFrameTime
            avgFrameCounter = 0
        }

        val finalStr = String.format(
            "%.0f/%.0f fps (%.2f ms)",
            currentFps,
            avgFps,
            avgFrameTime / 1000000.0
        )

        minecraft.textRenderer.drawWithShadow(finalStr, 2f, 2f, 0xFFFFFF)
    }

    private val avgFt: Double
        get() {
            var total: Long = 0
            val count = if (isFilled) SAMPLE_SIZE else frameIndex
            for (i in 0..<count) {
                total += frameTimings[i]
            }
            return total.toDouble() / count
        }

    private const val SAMPLE_SIZE = 700
    private const val FPS_UPDATE_INTERVAL = 100 // need an update interval, or it'll be too hard to read with inconsistent fps
    private const val AVG_UPDATE_INTERVAL = 700
}
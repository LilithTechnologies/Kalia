package re.lilith.kalia.voxel.render

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.graph.RenderGraphBuilder
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.TextureSizing
import re.lilith.kalia.voxel.SvoSettings

/**
 * Wires the voxel lighting chain into the frame graph.
 *
 * trace -> temporal -> a-trous xN. The chain traces its own primary rays, so it depends on nothing
 * the world pass produces and runs before it; the traced terrain then samples the finished light
 * where it draws.
 *
 * It deliberately does not composite over the finished frame. Doing that lit every pixel with
 * whatever surface the tracer had found behind it, so entities, particles and the hand all picked
 * up the lighting of the terrain they happened to be standing in front of.
 */
object SvoPasses {
    /** The finished lighting for a frame, and the geometry buffer describing what it belongs to. */
    class Lighting(val light: TextureHandle, val geometry: TextureHandle)

    /**
     * Adds the lighting chain.
     *
     * @param worldScale resolution of the world targets relative to the back buffer
     * @param lightmap Minecraft's light table, declared so the graph keeps it sampleable
     * @return the handles the terrain should read, or null when the chain could not be built
     */
    fun addLighting(
        builder: RenderGraphBuilder,
        worldScale: Float,
        format: TextureFormat,
        lightmap: TextureHandle?,
    ): Lighting? {
        val history = SvoRenderer.history ?: return null
        val historyLight = history.currentLight ?: return null
        val historyGeometry = history.currentGeometry ?: return null
        val priorLight = history.previousLight ?: return null
        val priorGeometry = history.previousGeometry ?: return null

        val sizing = TextureSizing.RelativeToBackbuffer(worldScale * SvoSettings.traceScale)

        with(builder) {
            val rawLight = texture("svo-light-raw", format, sizing)
            val rawGeometry = texture("svo-geometry-raw", format, sizing)

            pass("svo/trace") {
                color(rawLight)
                color(rawGeometry)
                lightmap?.let(::reads)
                draw { SvoRenderer.trace(this) }
            }

            val accumulatedLight = import("svo-history-light", historyLight)
            val accumulatedGeometry = import("svo-history-geometry", historyGeometry)
            // Last frame's pair is imported too, purely so the graph transitions it out of the
            // colour-attachment layout it was left in before the shader samples it.
            val reprojectedLight = import("svo-history-light-prior", priorLight)
            val reprojectedGeometry = import("svo-history-geometry-prior", priorGeometry)

            pass("svo/temporal") {
                color(accumulatedLight)
                color(accumulatedGeometry)
                reads(setOf(rawLight, rawGeometry, reprojectedLight, reprojectedGeometry))
                // The persistent pair is what next frame reprojects from, so this pass has to run
                // even on a frame whose lighting nothing ends up reading.
                sideEffects()
                draw {
                    SvoRenderer.temporal(
                        context = this,
                        light = rawLight,
                        geometry = rawGeometry,
                        historyLight = reprojectedLight,
                        historyGeometry = reprojectedGeometry,
                    )
                }
            }

            var filtered = accumulatedLight
            val passes = if (SvoSettings.denoiseEnabled) SvoSettings.denoisePasses else 0
            if (passes > 0) {
                val ping = texture("svo-light-ping", format, sizing)
                val pong = texture("svo-light-pong", format, sizing)
                for (index in 0 until passes) {
                    val output = if (index % 2 == 0) ping else pong
                    val input = filtered
                    val step = (1 shl index).toFloat()
                    pass("svo/denoise-$index") {
                        color(output)
                        reads(setOf(input, accumulatedGeometry))
                        draw { SvoRenderer.denoise(this, input, accumulatedGeometry, step) }
                    }
                    filtered = output
                }
            }

            return Lighting(filtered, accumulatedGeometry)
        }
    }
}

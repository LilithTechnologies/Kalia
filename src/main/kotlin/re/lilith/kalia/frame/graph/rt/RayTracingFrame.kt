package re.lilith.kalia.frame.graph.rt

import org.joml.Matrix4f
import re.lilith.kalia.gl.GlEnums
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.rendering.world.WorldCameraHistory
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The settings and camera state one frame of ray tracing runs against.
 *
 * Snapshotted on the game thread alongside the rest of the frame shape, so the
 * render thread never reads an option while it is being changed.
 */
object RayTracingFrame {
    @Volatile
    var enabled = false
        private set

    @Volatile
    var quality = RayTracingQuality.BALANCED
        private set

    @Volatile
    var traceScale = TraceScale.HALF
        private set

    @Volatile
    var indirectIntensity = 0.4f
        private set

    @Volatile
    var occlusionIntensity = 0.85f
        private set

    @Volatile
    var skyLight = 0.8f
        private set

    @Volatile
    var emissiveIntensity = 1.5f
        private set

    @Volatile
    var reflections = false
        private set

    @Volatile
    var reflectionIntensity = 1f
        private set

    @Volatile
    var denoiser = DenoiserMode.FULL
        private set

    @Volatile
    var denoiserStrength = 1f
        private set

    @Volatile
    var filterIterations = 4
        private set

    @Volatile
    var accumulationFrames = 48
        private set

    @Volatile
    var debugView = RayTracingDebugView.OFF
        private set

    @Volatile
    var sunIntensity = 1.6f
        private set

    @Volatile
    var skyAmbient = 0.6f
        private set

    @Volatile
    var blockLightIntensity = 1.1f
        private set

    @Volatile
    var exposure = 0.12f
        private set

    /** Fog the world was rendered with, so lit terrain fades into the same haze the sky does. */
    @Volatile
    var fogMode = 0
        private set

    @Volatile
    var fogStart = 0f
        private set

    @Volatile
    var fogEnd = 1f
        private set

    @Volatile
    var fogDensity = 0f
        private set

    /** Sky and fog tint the world was cleared to, used as the radiance of a ray that escapes. */
    @Volatile
    var environment: Color = Color.BLACK
        private set

    /**
     * Direction towards the sun, in the camera-relative world space the scene is
     * traced in. Below the horizon this is the moon instead, which is what makes
     * night lighting come from the right place rather than nowhere.
     */
    @Volatile
    var sunX = 0f
        private set

    @Volatile
    var sunY = 1f
        private set

    @Volatile
    var sunZ = 0f
        private set

    /**
     * The true direction of the sun, which keeps pointing at the sun after it
     * sets rather than flipping to the moon. The atmosphere needs the real one:
     * a sun below the horizon is what makes a sunset look like a sunset.
     */
    @Volatile
    var trueSunX = 0f
        private set

    @Volatile
    var trueSunY = 1f
        private set

    @Volatile
    var trueSunZ = 0f
        private set

    /** Camera altitude in blocks, which decides how much atmosphere is overhead. */
    @Volatile
    var cameraAltitude = 64f
        private set

    /** How much light the sun or moon is currently delivering, before colouring. */
    @Volatile
    var sunStrength = 0f
        private set

    /** True while the moon is the dominant light, which is dimmer and cooler. */
    @Volatile
    var night = false
        private set

    @Volatile
    var hasHistory = false
        private set

    @Volatile
    var frameIndex = 0
        private set

    /** Clip space of this frame back to clip space of the previous one. */
    val reprojection = Matrix4f()

    /** Undoes the projection, recovering a view-space position from a depth sample. */
    val inverseProjection = Matrix4f()

    /**
     * Clip space straight back to the camera-relative world space chunk geometry
     * is positioned in, so the tracer reaches scene coordinates in one transform.
     */
    val inverseViewProjection = Matrix4f()

    /**
     * The two projection terms that turn a device depth into a linear one:
     * `linear = depthB / (deviceDepth + depthA)`. Passing these costs eight bytes
     * where a second matrix would cost sixty-four.
     */
    @Volatile
    var depthA = 0f
        private set

    @Volatile
    var depthB = 0f
        private set

    private val previousSignature = IntArray(1)

    fun capture() {
        val settings = RayTracingSettings
        val state = WorldFrame.consumedState

        enabled = settings.enabled
        quality = settings.quality
        traceScale = settings.traceScale
        indirectIntensity = settings.indirectIntensity
        occlusionIntensity = settings.occlusionIntensity
        skyLight = settings.skyLight
        emissiveIntensity = settings.emissiveIntensity
        reflections = settings.reflections
        reflectionIntensity = settings.reflectionIntensity
        denoiser = settings.denoiser
        denoiserStrength = settings.denoiserStrength
        filterIterations = settings.filterIterations.coerceIn(0, 5)
        accumulationFrames = settings.accumulationFrames.coerceIn(2, 256)
        debugView = settings.debugView
        environment = WorldFrame.consumedClearColor
        sunIntensity = settings.sunIntensity
        skyAmbient = settings.skyAmbient
        blockLightIntensity = settings.blockLightIntensity
        exposure = settings.exposure

        if (!enabled || !state.active) {
            hasHistory = false
            return
        }

        captureCelestial(state)
        captureFog(state)

        inverseProjection.set(state.terrainProjection).invert()
        inverseViewProjection.set(state.terrainProjection).mul(state.view).invert()
        depthA = state.terrainProjection.m22()
        depthB = state.terrainProjection.m32()
        reprojection.set(WorldCameraHistory.reprojection)

        // Changing what the tracer produces makes accumulated history wrong
        // rather than merely stale, so it is dropped instead of blended out.
        val signature = settingsSignature()
        val changed = previousSignature[0] != signature
        previousSignature[0] = signature

        hasHistory = WorldCameraHistory.hasHistory && !changed
        frameIndex++
    }

    /**
     * Works out where the sun is from the same angle the sky renders with.
     *
     * The celestial quad is drawn straight up and then rotated by
     * `rotateY(-90) * rotateX(skyAngle * 360)`, which puts the sun at
     * `(-sin, cos, 0)` for that angle. Deriving it rather than inventing one keeps
     * shadows pointing the same way as the sun the player can see.
     */
    private fun captureCelestial(state: WorldFrameState) {
        val angle = state.skyAngle * 2.0 * Math.PI
        var x = (-sin(angle)).toFloat()
        var y = cos(angle).toFloat()

        // The atmosphere is driven by where the sun actually is, including when
        // that is below the horizon. Shading uses whichever body is up.
        trueSunX = x
        trueSunY = y
        trueSunZ = 0f
        cameraAltitude = state.cameraY.toFloat()

        night = y <= 0f
        if (night) {
            // Past sunset the moon takes over, opposite the sun.
            x = -x
            y = -y
        }

        sunX = x
        sunY = y
        sunZ = 0f

        // Fade the light out across the horizon rather than snapping it off, and
        // let rain damp it, which is what makes a storm read as overcast.
        val elevation = (y / HORIZON_FADE).coerceIn(0f, 1f)
        val weather = 1f - state.rainGradient * RAIN_DIMMING
        sunStrength = elevation * weather * (if (night) MOON_STRENGTH else 1f)
    }

    /**
     * Carries the world's fog over to the lighting pass.
     *
     * The geometry buffer path never applies fog, because a geometry buffer has no
     * business holding a colour that is not the surface's own. It is applied once,
     * after the surface is lit, which is also where it lines up with the sky.
     */
    private fun captureFog(state: WorldFrameState) {
        val fog = state.worldFog
        fogMode = if (!fog.enabled) {
            FOG_OFF
        } else {
            when (fog.mode) {
                GlEnums.FogMode.LINEAR -> FOG_LINEAR
                GlEnums.FogMode.EXP -> FOG_EXP
                GlEnums.FogMode.EXP2 -> FOG_EXP2
            }
        }
        fogStart = fog.start
        fogEnd = fog.end
        fogDensity = fog.density
    }

    private fun settingsSignature(): Int {
        var result = quality.ordinal
        result = 31 * result + traceScale.ordinal
        result = 31 * result + denoiser.ordinal
        result = 31 * result + filterIterations
        result = 31 * result + accumulationFrames
        result = 31 * result + skyLight.toRawBits()
        result = 31 * result + emissiveIntensity.toRawBits()
        result = 31 * result + (if (reflections) 1 else 0)
        return result
    }

    /** Elevation over which the sun fades in and out at the horizon. */
    private const val HORIZON_FADE = 0.2f

    /** How far a full storm dims the sky's light. */
    private const val RAIN_DIMMING = 0.85f

    const val FOG_OFF = 0
    const val FOG_LINEAR = 1
    const val FOG_EXP = 2
    const val FOG_EXP2 = 3

    /** Moonlight relative to sunlight. */
    private const val MOON_STRENGTH = 0.06f
}

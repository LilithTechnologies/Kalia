package re.lilith.kalia.voxel.render

import org.joml.Matrix4f
import re.lilith.kalia.rendering.world.WorldCameraHistory
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.VoxelFormat
import re.lilith.kalia.voxel.VoxelWorld
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything the voxel passes need about a frame, captured on the game thread and read on the
 * render thread.
 *
 * The renderer never touches the world or the settings objects directly, so a chunk loading or a
 * slider moving mid-frame cannot change what a half-recorded frame is drawing.
 */
class SvoFrameState {
    var active = false

    val inverseViewProjection = Matrix4f()
    val viewProjection = Matrix4f()
    val reprojection = Matrix4f()

    var hasHistory = false

    /** Octree minimum corner, in blocks relative to the camera. */
    var treeMinX = 0f
    var treeMinY = 0f
    var treeMinZ = 0f

    var levels = 0
    var rootNode = 0

    var sunX = 0f
    var sunY = 1f
    var sunZ = 0f
    var sunIntensity = 0f

    var sunRed = 1f
    var sunGreen = 1f
    var sunBlue = 1f
    var skyAmbient = 0.5f

    var skyRed = 0.5f
    var skyGreen = 0.6f
    var skyBlue = 0.8f

    var fogRed = 0.5f
    var fogGreen = 0.6f
    var fogBlue = 0.8f
    var fogStart = 32f
    var fogEnd = 256f

    var frameIndex = 0

    /** The projection's vertical scale, from which the per-pixel cone footprint is derived. */
    var projectionScaleY = 1f

    /**
     * How wide one pixel's cone is per block of distance. Drives both texture mip selection and,
     * once scaled by the level-of-detail bias, the point where the octree descent stops.
     */
    fun footprint(targetHeight: Int): Float {
        if (targetHeight <= 0 || projectionScaleY <= 0f) {
            return 0f
        }
        return 2f / (projectionScaleY * targetHeight)
    }

    fun reset() {
        active = false
    }

    /**
     * Snapshots the camera, the sun and the octree anchor.
     *
     * @return true when the voxel scene is worth drawing this frame.
     */
    fun capture(world: WorldFrameState, frame: Int): Boolean {
        active = false
        if (!SvoSettings.enabled || !world.active || VoxelWorld.liveSections == 0) {
            return false
        }

        frameIndex = frame

        viewProjection.set(WorldCameraHistory.viewProjection)
        inverseViewProjection.set(viewProjection).invert()
        reprojection.set(WorldCameraHistory.reprojection)
        hasHistory = WorldCameraHistory.hasHistory

        levels = VoxelWorld.levels
        rootNode = VoxelWorld.rootNode
        projectionScaleY = world.terrainProjection.m11()

        val edge = VoxelFormat.BRICK_EDGE.toDouble()
        treeMinX = (VoxelWorld.originBrickX * edge - world.cameraX).toFloat()
        treeMinY = (VoxelWorld.originBrickY * edge - world.cameraY).toFloat()
        treeMinZ = (VoxelWorld.originBrickZ * edge - world.cameraZ).toFloat()

        captureSun(world)
        captureAtmosphere(world)

        active = true
        return true
    }

    /**
     * Vanilla swings the celestial bodies around the world X axis after a quarter turn about Y, so
     * the sun's direction works out to `(-sin, cos, 0)` of the sky angle: straight up at noon and
     * due east at dawn.
     */
    private fun captureSun(world: WorldFrameState) {
        val angle = world.skyAngle * TAU
        var x = -sin(angle)
        var y = cos(angle)

        val daylight = ((y * 1.6f).coerceIn(0f, 1f)) * (1f - world.rainGradient * 0.75f)
        if (y < 0f) {
            // After dusk the moon takes over: same track, opposite side, a fraction of the light.
            x = -x
            y = -y
            sunIntensity = 0.05f * (1f - world.rainGradient * 0.5f)
            sunRed = 0.55f
            sunGreen = 0.62f
            sunBlue = 0.85f
        } else {
            sunIntensity = daylight
            // Low sun reddens, which is most of what makes a sunrise read as one.
            val horizon = (1f - (y * 2.5f).coerceIn(0f, 1f))
            sunRed = 1.0f
            sunGreen = 0.96f - 0.34f * horizon
            sunBlue = 0.90f - 0.55f * horizon
        }

        sunX = x
        sunY = y
        sunZ = 0f
    }

    private fun captureAtmosphere(world: WorldFrameState) {
        skyRed = world.skyRed
        skyGreen = world.skyGreen
        skyBlue = world.skyBlue

        val fog = world.worldFog
        fogRed = fog.red
        fogGreen = fog.green
        fogBlue = fog.blue
        if (fog.enabled && fog.end > fog.start) {
            fogStart = fog.start
            fogEnd = fog.end
        } else {
            fogStart = 1e6f
            fogEnd = 1e6f
        }

        // Overcast skies scatter more light around; a clear night scatters almost none.
        val overhead = abs(sunY)
        val day = (sunY * 2f).coerceIn(0f, 1f)
        skyAmbient = (0.18f + 0.42f * day + 0.12f * world.rainGradient * overhead)
            .coerceIn(0.08f, 0.85f)
    }

    private companion object {
        const val TAU = (2.0 * Math.PI).toFloat()
    }
}

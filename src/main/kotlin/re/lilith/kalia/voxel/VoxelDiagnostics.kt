package re.lilith.kalia.voxel

import re.lilith.kalia.voxel.render.SvoRenderer
import re.lilith.kalia.voxel.render.SvoScene

/**
 * Self-reporting for the voxel pipeline, surfaced on the debug overlay.
 *
 * The interesting failure modes are silent: an octree that never gets filled, an anchor that puts
 * the camera outside the root, a tree that reaches the GPU as zeroes. Rather than guess at which
 * one is happening, the overlay casts a handful of probe rays through the CPU copy of the tree
 * every frame and reports what they found. If the probes report sensible distances, the data and
 * the coordinate frame are right and any remaining problem is on the GPU side of the fence.
 */
object VoxelDiagnostics {
    private val caster = VoxelRaycaster()

    private var probeDown = -1.0
    private var probeNorth = -1.0
    private var probeUp = -1.0
    private var probeSteps = 0
    private var probedFrame = -1

    /**
     * Casts the probe rays. Runs on the game thread alongside [VoxelWorld.tick], which is the only
     * other thing allowed to touch the arenas.
     */
    fun probe(cameraX: Double, cameraY: Double, cameraZ: Double, frame: Int) {
        if (probedFrame == frame || VoxelWorld.liveSections == 0) {
            return
        }
        probedFrame = frame

        val edge = VoxelFormat.BRICK_EDGE.toDouble()
        val localX = cameraX - VoxelWorld.originBrickX * edge
        val localY = cameraY - VoxelWorld.originBrickY * edge
        val localZ = cameraZ - VoxelWorld.originBrickZ * edge

        probeDown = distance(localX, localY, localZ, 0.0, -1.0, 0.0)
        probeSteps = caster.steps
        probeNorth = distance(localX, localY, localZ, 0.0, 0.0, -1.0)
        probeUp = distance(localX, localY, localZ, 0.0, 1.0, 0.0)
    }

    private fun distance(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double): Double =
        if (caster.cast(x, y, z, dx, dy, dz, 256.0)) caster.hitDistance else -1.0

    /** Lines for the debug overlay, newest state each call. */
    fun report(into: MutableList<String>) {
        if (!SvoSettings.enabled) {
            into += "svo off"
            return
        }

        val bricks = VoxelWorld.bricks
        val nodes = VoxelWorld.nodes
        into += "svo traced" +
            (if (SvoScene.isActive) "" else "  SCENE INACTIVE") +
            (if (SvoRenderer.ready) "" else "  GPU NOT READY") +
            "  sections ${VoxelWorld.liveSections}" +
            "  queued ${VoxelWorld.queuedBricks}" +
            (if (VoxelWorld.dropped > 0) "  dropped ${VoxelWorld.dropped}" else "")

        into += "svo mem  bricks ${mib(bricks.usedWords)}/${mib(bricks.capacityWords)}" +
            " (free ${mib(bricks.freeWords)})" +
            "  nodes ${mib(nodes.usedNodes * VoxelFormat.NODE_WORDS)}" +
            "  runs ${nodes.liveRuns}" +
            "  upload ${SvoRenderer.lastUploadBytes / 1024}KiB/f"

        val origin = "${VoxelWorld.originBrickX * VoxelFormat.BRICK_EDGE}," +
            "${VoxelWorld.originBrickY * VoxelFormat.BRICK_EDGE}," +
            "${VoxelWorld.originBrickZ * VoxelFormat.BRICK_EDGE}"
        into += "svo tree  levels ${VoxelWorld.levels}" +
            "  span ${VoxelWorld.span * VoxelFormat.BRICK_EDGE} blocks" +
            "  origin $origin" +
            "  root ${VoxelWorld.rootNode}"

        into += "svo probe down ${length(probeDown)}" +
            "  north ${length(probeNorth)}" +
            "  up ${length(probeUp)}" +
            "  steps $probeSteps"
    }

    private fun length(value: Double): String =
        if (value < 0.0) "miss" else String.format(java.util.Locale.ROOT, "%.2f", value)

    private fun mib(words: Int): String =
        String.format(java.util.Locale.ROOT, "%.1fMiB", words * 4.0 / (1 shl 20))
}

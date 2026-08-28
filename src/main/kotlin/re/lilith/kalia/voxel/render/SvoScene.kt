package re.lilith.kalia.voxel.render

import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.VoxelDiagnostics
import re.lilith.kalia.voxel.VoxelWorld

/**
 * Double-buffered handoff of the voxel frame state from the game thread to the render thread,
 * mirroring how [re.lilith.kalia.rendering.world.WorldFrame] hands over its submissions.
 */
object SvoScene {
    private val states = Array(2) { SvoFrameState() }
    private var producingIndex = 0
    private var producing = states[0]

    @Volatile
    private var consuming = states[0]

    private var frame = 0

    /** The state the render thread should draw. */
    val current: SvoFrameState get() = consuming

    val isActive: Boolean get() = consuming.active

    /**
     * Applies queued voxel work and snapshots the frame. Runs on the game thread, right after the
     * world state has been extracted.
     */
    fun collect(world: WorldFrameState) {
        val state = producing
        state.reset()
        if (!SvoSettings.enabled || !world.active) {
            return
        }

        VoxelWorld.tick(world.cameraX, world.cameraY, world.cameraZ)
        state.capture(world, frame)
        VoxelDiagnostics.probe(world.cameraX, world.cameraY, world.cameraZ, frame)
        frame++
    }

    fun publish() {
        consuming = producing
        producingIndex = (producingIndex + 1) % states.size
        producing = states[producingIndex]
    }

    fun discard() {
        states.forEach(SvoFrameState::reset)
    }
}

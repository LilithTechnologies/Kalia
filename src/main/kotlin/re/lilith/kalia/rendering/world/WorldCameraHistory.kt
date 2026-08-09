package re.lilith.kalia.rendering.world

import org.joml.Matrix4f

object WorldCameraHistory {
    val viewProjection = Matrix4f()
    val previousViewProjection = Matrix4f()

    val reprojection = Matrix4f()

    var hasHistory = false
        private set

    private val inverseViewProjection = Matrix4f()
    private val cameraDelta = Matrix4f()

    private var previousCameraX = 0.0
    private var previousCameraY = 0.0
    private var previousCameraZ = 0.0
    private var seenFrame = false

    fun update(state: WorldFrameState) {
        previousViewProjection.set(viewProjection)
        viewProjection.set(state.terrainProjection).mul(state.view)

        hasHistory = seenFrame
        if (hasHistory) {
            cameraDelta.translation(
                (state.cameraX - previousCameraX).toFloat(),
                (state.cameraY - previousCameraY).toFloat(),
                (state.cameraZ - previousCameraZ).toFloat(),
            )
            inverseViewProjection.set(viewProjection).invert()
            reprojection.set(previousViewProjection).mul(cameraDelta).mul(inverseViewProjection)
        } else {
            reprojection.identity()
        }

        previousCameraX = state.cameraX
        previousCameraY = state.cameraY
        previousCameraZ = state.cameraZ
        seenFrame = true
    }

    fun reset() {
        seenFrame = false
        hasHistory = false
        reprojection.identity()
    }
}

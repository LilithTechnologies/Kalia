package re.lilith.kalia.rendering.world

import re.lilith.kalia.renderer.geometry.Color

internal class WorldFramePayload {
    var clearColor: Color = Color.BLACK

    val submissions = WorldSubmissions()
    val state = WorldFrameState()

    var submissionCount = 0

    fun reset() {
        submissions.reset()
        submissionCount = 0
    }

    fun discard() {
        submissions.reset()
        state.reset()
        submissionCount = 0
    }
}

package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.MinecraftClient
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldRecorder
import re.lilith.kalia.rendering.world.WorldSubmissions

object HandSubmitter {
    private const val CLEARED_DEPTH = 1f

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active || !state.handVisible) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        EntitySubmitter.applyWorldState(state)

        WorldRecorder.record(
            submissions = submissions,
            phase = WorldPhase.HAND,
            material = WorldMaterial.TERRAIN_CUTOUT,
            label = "Hand",
        ) {
            GameFrame.current?.clearAttachments(depth = CLEARED_DEPTH)

            renderer.invokeRenderHand(state.tickDelta, state.anaglyphFilter)
            renderer.invokeRenderDebugCrosshair(state.tickDelta)
        }
    }
}

package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.MinecraftClient
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldRecorder
import re.lilith.kalia.rendering.world.WorldSubmissions

object WeatherSubmitter {
    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return
        val camera = client.cameraEntity ?: return
        val worldRenderer = client.worldRenderer ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        if (state.weatherVisible) {
            EntitySubmitter.applyWorldState(state)
            WorldRecorder.record(
                submissions = submissions,
                phase = WorldPhase.WEATHER,
                material = WorldMaterial.TERRAIN_CUTOUT,
                label = "Weather",
            ) {
                GlState.depthWrite = false
                GlState.cullEnabled = true
                renderer.invokeRenderWeather(state.tickDelta)
                GlState.depthWrite = true
            }
        }

        if (state.borderVisible) {
            EntitySubmitter.applyWorldState(state)
            WorldRecorder.record(
                submissions = submissions,
                phase = WorldPhase.WORLD_BORDER,
                material = WorldMaterial.TERRAIN_CUTOUT,
                label = "World border",
            ) {
                worldRenderer.renderWorldBorder(camera, state.tickDelta)
            }
        }
    }
}

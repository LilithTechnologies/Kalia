package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.client.particle.ParticleManager
import net.minecraft.client.render.DiffuseLighting
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.mixins.access.ParticleManagerAccess
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldRecorder
import re.lilith.kalia.rendering.world.WorldSubmissions

object ParticleSubmitter {
    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return
        val camera = client.cameraEntity ?: return
        val particles = client.particleManager ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        if (!hasParticles(particles)) {
            return
        }

        EntitySubmitter.applyWorldState(state)

        WorldRecorder.record(
            submissions = submissions,
            phase = WorldPhase.PARTICLES,
            material = WorldMaterial.TERRAIN_CUTOUT,
            label = "Particles",
        ) {
            renderer.invokeEnableLightmap()
            particles.m_08636098(camera, state.tickDelta)
            DiffuseLighting.disable()
            state.worldFog.apply(enable = true)
            particles.renderParticles(camera, state.tickDelta)
            renderer.invokeDisableLightmap()
        }
    }

    private fun hasParticles(particles: ParticleManager): Boolean {
        val layers = (particles as? ParticleManagerAccess)?.particles ?: return true
        for (layer in layers) {
            for (list in layer) {
                if (list.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }
}

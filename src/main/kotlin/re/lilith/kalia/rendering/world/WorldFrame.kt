package re.lilith.kalia.rendering.world

import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.rendering.world.sky.CloudSubmitter
import re.lilith.kalia.rendering.world.sky.SkyMeshes
import re.lilith.kalia.rendering.world.entity.EntitySubmitter
import re.lilith.kalia.rendering.world.entity.OverlaySubmitter
import re.lilith.kalia.rendering.world.entity.HandSubmitter
import re.lilith.kalia.rendering.world.entity.ParticleSubmitter
import re.lilith.kalia.rendering.world.entity.WeatherSubmitter
import re.lilith.kalia.rendering.world.sky.SkySubmitter
import re.lilith.kalia.rendering.world.terrain.TerrainSubmitter

object WorldFrame {
    private val submissions = WorldSubmissions()

    val isActive get() = WorldFrameState.active

    var lastSubmissions = 0
        private set

    fun collect(tickDelta: Float) {
        submissions.reset()
        lastSubmissions = 0

        WorldFrameTimings.begin()
        val active = WorldExtract.extract(tickDelta)
        WorldFrameTimings.end(WorldFrameTimings.EXTRACT)
        if (!active) {
            return
        }

        val terrain = TerrainSubmitter.prepare(WorldFrameState)
        WorldFrameTimings.end(WorldFrameTimings.TERRAIN_PREPARE)

        SkySubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.SKY)

        CloudSubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.CLOUDS)

        if (terrain) {
            TerrainSubmitter.submit(WorldFrameState, submissions)
        }
        WorldFrameTimings.end(WorldFrameTimings.TERRAIN_SUBMIT)

        EntitySubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.ENTITIES)

        OverlaySubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.OVERLAYS)

        ParticleSubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.PARTICLES)

        WeatherSubmitter.submit(WorldFrameState, submissions)
        HandSubmitter.submit(WorldFrameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.WEATHER_HAND)

        lastSubmissions = submissions.size

        // The GUI walk runs next and inherits this state, so leave it as vanilla leaves
        // it entering the HUD rather than as the last world submitter left it.
        WorldExecutor.restoreDefaults()
    }

    // Phase order is the draw order. Empty phases cost a branch, so an unmigrated system
    // is free until something submits into it.
    fun draw(pass: PassContext) {
        GameFrame.record(pass) {
            for (phase in WorldPhase.VALUES) {
                WorldExecutor.draw(pass, submissions, phase)
            }
        }
    }

    fun discard() {
        submissions.reset()
        WorldFrameState.reset()
        lastSubmissions = 0
    }

    fun release() {
        SkyMeshes.release()
        discard()
    }
}

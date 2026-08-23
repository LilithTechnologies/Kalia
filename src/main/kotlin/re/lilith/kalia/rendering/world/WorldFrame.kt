package re.lilith.kalia.rendering.world

import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.graph.BatchStats
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.geometry.Color
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
    private val payloads = Array(PAYLOADS) { WorldFramePayload() }
    private var producingIndex = 0

    private var producing: WorldFramePayload = payloads[0]

    @Volatile
    private var consuming: WorldFramePayload = payloads[0]

    val state: WorldFrameState get() = producing.state

    val consumedState: WorldFrameState get() = consuming.state

    val isActive: Boolean get() = consuming.state.active

    val lastSubmissions: Int get() = consuming.submissionCount

    val consumedClearColor: Color get() = consuming.clearColor

    fun collect(tickDelta: Float) {
        val payload = producing
        payload.reset()

        BatchStats.beginFrame()
        WorldFrameTimings.begin()
        val active = WorldExtract.extract(payload.state, tickDelta)
        WorldFrameTimings.end(WorldFrameTimings.EXTRACT)
        if (!active) {
            return
        }

        val submissions = payload.submissions
        val frameState = payload.state

        TerrainSubmitter.prepareState(frameState)
        WorldFrameTimings.end(WorldFrameTimings.TERRAIN_PREPARE)

        SkySubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.SKY)

        CloudSubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.CLOUDS)

        EntitySubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.ENTITIES)

        OverlaySubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.OVERLAYS)

        OcclusionSubmitter.submit(frameState, submissions)

        ParticleSubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.PARTICLES)

        WeatherSubmitter.submit(frameState, submissions)
        HandSubmitter.submit(frameState, submissions)
        WorldFrameTimings.end(WorldFrameTimings.WEATHER_HAND)

        payload.submissionCount = submissions.size
    }

    fun collectTerrain() {
        val payload = producing
        val frameState = payload.state
        if (frameState.active) {
            val terrain = TerrainSubmitter.prepare(frameState)
            if (terrain) {
                TerrainSubmitter.submit(frameState, payload.submissions)
            }
            WorldFrameTimings.end(WorldFrameTimings.TERRAIN_SUBMIT)
            payload.submissionCount = payload.submissions.size
        }
        payload.clearColor = GlState.clearColor

        WorldExecutor.restoreDefaults()
    }

    fun publish() {
        consuming = producing
        producingIndex = (producingIndex + 1) % payloads.size
        producing = payloads[producingIndex]
    }

    fun draw(pass: PassContext) {
        val payload = consuming
        GameFrame.record(pass) {
            for (phase in WorldPhase.VALUES) {
                WorldExecutor.draw(pass, payload.state, payload.submissions, phase)
            }
        }
    }

    fun discard() {
        payloads.forEach(WorldFramePayload::discard)
    }

    fun release() {
        SkyMeshes.release()
        discard()
    }

    private const val PAYLOADS = 2
}

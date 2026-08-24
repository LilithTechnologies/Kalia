package re.lilith.kalia.rendering.world.terrain

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.texture.SpriteAtlasTexture
import org.embeddedt.embeddium.impl.render.viewport.Viewport
import org.embeddedt.embeddium.impl.render.viewport.frustum.SimpleFrustum
import org.joml.Vector3d
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer
import org.taumc.celeritas.impl.render.terrain.matrix.PrimitiveChunkMatrixGetter
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldSubmission
import re.lilith.kalia.rendering.world.WorldSubmissions

object TerrainSubmitter {
    private val origin = Vector3d()
    private val projectionValues = FloatArray(16)
    private val viewValues = FloatArray(16)

    private var frame = 0

    fun prepareState(state: WorldFrameState) {
        if (!state.active) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return

        bindTerrainTextures(client)

        state.terrainProjection.get(projectionValues)
        state.view.get(viewValues)
        PrimitiveChunkMatrixGetter.update(projectionValues, viewValues)
    }

    fun prepare(state: WorldFrameState): Boolean {
        if (!state.active) {
            return false
        }
        val client = MinecraftClient.getInstance() ?: return false
        val player = client.player ?: return false
        val renderer = CeleritasWorldRenderer.instanceNullable() ?: return false

        return runCatching {
            renderer.setupTerrain(
                viewportFor(state),
                CeleritasWorldRenderer.captureCameraState(state.tickDelta.toDouble()),
                frame++,
                player.noClip,
                false,
            )
            true
        }.getOrElse { failure ->
            KaliaMod.LOGGER.error("Terrain setup failed; the world will not be drawn this frame.", failure)
            false
        }
    }

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active || CeleritasWorldRenderer.instanceNullable() == null) {
            return
        }

        submitLayer(state, submissions, WorldPhase.TERRAIN_SOLID, RenderLayer.SOLID, WorldMaterial.TERRAIN_OPAQUE)
        submitLayer(
            state,
            submissions,
            WorldPhase.TERRAIN_CUTOUT_MIPPED,
            RenderLayer.CUTOUT_MIPPED,
            WorldMaterial.TERRAIN_CUTOUT,
        )
        submitLayer(state, submissions, WorldPhase.TERRAIN_CUTOUT, RenderLayer.CUTOUT, WorldMaterial.TERRAIN_CUTOUT)
        submitLayer(
            state,
            submissions,
            WorldPhase.TERRAIN_TRANSLUCENT,
            RenderLayer.TRANSLUCENT,
            WorldMaterial.TERRAIN_TRANSLUCENT,
        )
    }

    private fun submitLayer(
        state: WorldFrameState,
        submissions: WorldSubmissions,
        phase: WorldPhase,
        layer: RenderLayer,
        material: WorldMaterial,
    ) {
        val x = state.cameraX
        val y = state.cameraY
        val z = state.cameraZ

        submissions.submit(
            WorldSubmission.Custom(phase = phase, material = material) {
                CeleritasWorldRenderer.instanceNullable()?.drawChunkLayer(layer, x, y, z)
            },
        )
    }

    private fun bindTerrainTextures(client: MinecraftClient) {
        val manager = client.textureManager ?: return
        val renderer = client.gameRenderer ?: return
        manager.bindTexture(renderer.lightmapTextureId)
        manager.bindTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX)
    }

    private fun viewportFor(state: WorldFrameState): Viewport = Viewport(
        SimpleFrustum(state.frustum),
        origin.set(state.frustumOriginX, state.frustumOriginY, state.frustumOriginZ),
    )
}

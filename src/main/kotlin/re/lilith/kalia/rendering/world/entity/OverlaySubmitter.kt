package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Tessellator
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.entity.player.PlayerEntity
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_ZERO
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.mixins.access.WorldRendererAccess
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldRecorder
import re.lilith.kalia.rendering.world.WorldSubmissions

object OverlaySubmitter {
    private const val NO_ALPHA_TEST = -1f
    private const val OVERLAY_ALPHA_CUTOUT = 0.1f

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return
        val camera = client.cameraEntity ?: return
        val worldRenderer = client.worldRenderer ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        val hit = client.result
        val player = camera as? PlayerEntity
        val outline = hit != null && player != null && renderer.invokeShouldRenderBlockOutline()
        val damage = (worldRenderer as? WorldRendererAccess)?.blockBreakingInfos?.isNotEmpty() ?: false

        if (!outline && !damage) {
            return
        }

        EntitySubmitter.applyWorldState(state)

        WorldRecorder.record(
            submissions = submissions,
            phase = WorldPhase.OVERLAYS,
            material = WorldMaterial.TERRAIN_OPAQUE,
            label = "Block overlays",
        ) {
            if (outline && hit != null && player != null) {
                ShaderUniforms.setAlphaCutout(NO_ALPHA_TEST)
                worldRenderer.drawBlockOutline(player, hit, 0, state.tickDelta)
                ShaderUniforms.setAlphaCutout(OVERLAY_ALPHA_CUTOUT)
            }

            if (damage) {
                GlState.blendEnabled = true
                GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ZERO)

                val atlas = client.textureManager?.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX)
                atlas?.pushFilter(false, false)
                val tessellator = Tessellator.getInstance()
                worldRenderer.drawBlockDamage(tessellator, tessellator.buffer, camera, state.tickDelta)
                atlas?.pop()

                GlState.blendEnabled = false
            }
        }
    }
}

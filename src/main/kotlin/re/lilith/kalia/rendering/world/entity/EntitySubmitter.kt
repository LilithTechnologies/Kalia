package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.DiffuseLighting
import org.lwjgl.opengl.GL11.GL_MODELVIEW
import org.lwjgl.opengl.GL11.GL_PROJECTION
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.pipeline.CullMode
import re.lilith.kalia.rendering.world.WorldFrameState
import re.lilith.kalia.rendering.world.WorldMaterial
import re.lilith.kalia.rendering.world.WorldPhase
import re.lilith.kalia.rendering.world.WorldRecorder
import re.lilith.kalia.rendering.world.WorldSubmissions

object EntitySubmitter {
    private const val ENTITY_ALPHA_CUTOUT = 0.1f

    private val cameraView = KaliaCameraView()

    val view: KaliaCameraView get() = cameraView

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active) {
            return
        }
        val client = MinecraftClient.getInstance() ?: return
        val camera = client.cameraEntity ?: return
        val worldRenderer = client.worldRenderer ?: return

        applyWorldState(state)
        cameraView.setPos(state.cameraX, state.cameraY, state.cameraZ)

        WorldRecorder.record(
            submissions = submissions,
            phase = WorldPhase.ENTITIES,
            material = WorldMaterial.TERRAIN_OPAQUE,
            label = "Entities",
        ) {
            DiffuseLighting.enableNormally()
            worldRenderer.renderEntities(camera, cameraView, state.tickDelta)
            DiffuseLighting.disable()
        }
    }

    fun applyWorldState(state: WorldFrameState) {
        MatrixState.matrixMode(GL_PROJECTION)
        MatrixState.loadIdentity()
        MatrixState.multiply(state.terrainProjection)
        MatrixState.matrixMode(GL_MODELVIEW)
        MatrixState.loadIdentity()
        MatrixState.multiply(state.view)
        MatrixState.flush()

        GlState.depthTest = true
        GlState.depthWrite = true
        GlState.blendEnabled = false
        GlState.cullEnabled = true
        GlState.cullFace = CullMode.BACK
        GlState.colorMask(red = true, green = true, blue = true, alpha = true)

        ShaderUniforms.setShaderColor(1f, 1f, 1f, 1f)
        ShaderUniforms.setAlphaCutout(ENTITY_ALPHA_CUTOUT)
        state.worldFog.apply(enable = true)
    }
}

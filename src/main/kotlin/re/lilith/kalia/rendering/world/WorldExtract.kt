package re.lilith.kalia.rendering.world

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.Camera
import net.minecraft.util.math.MathHelper
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_GREATER
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlEnums.GL_MODELVIEW
import re.lilith.kalia.gl.GlEnums.GL_PROJECTION
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.mixins.access.WorldRendererAccess
import re.lilith.kalia.platform.KaliaMod
import kotlin.math.floor

object WorldExtract {
    private const val WORLD_ALPHA_CUTOUT = 0.5f

    private const val NEAR_PLANE = 0.05
    private const val SKY_FAR_SCALE = 2.0f
    private const val CLOUD_FAR_SCALE = 4.0f

    private const val MINIMUM_SKY_VIEW_DISTANCE = 4

    private const val CLOUD_LAYER_Y = 128.0

    private const val CLOUD_WRAP = 2048.0

    private val scratch = FloatArray(3)
    private val viewProjection = Matrix4f()
    private val inverseView = Matrix4f()
    private val frustumOffset = Vector3f()

    fun extract(tickDelta: Float): Boolean {
        val state = WorldFrameState
        state.reset()

        val client = MinecraftClient.getInstance() ?: return false
        client.world ?: return false
        val camera = client.cameraEntity ?: return false
        val renderer = client.gameRenderer as? GameRendererAccess ?: return false

        state.active = true
        state.tickDelta = tickDelta
        state.anaglyphFilter = WorldFrameState.DISABLED_ANAGLYPH

        runCatching {
            renderer.invokeUpdateTargetedEntity(tickDelta)
        }.onFailure { KaliaMod.LOGGER.debug("World state could not be updated this frame.", it) }

        GlBridge.enableDepthTest()
        GlBridge.enableAlphaTest()
        GlBridge.alphaFunc(GL_GREATER, WORLD_ALPHA_CUTOUT)

        state.cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta
        state.cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta
        state.cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta

        state.renderX = camera.prevTickX + (camera.x - camera.prevTickX) * tickDelta
        state.renderY = camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta
        state.renderZ = camera.prevTickZ + (camera.z - camera.prevTickZ) * tickDelta

        extractCamera(client, renderer, tickDelta, state)
        client.player?.let { Camera.update(it, client.options.perspective == 2) }
        extractFog(renderer, tickDelta, state)
        extractSky(client, tickDelta, state)
        extractClouds(client, tickDelta, state)
        extractOverlays(client, renderer, tickDelta, state)

        return true
    }

    private fun extractCamera(
        client: MinecraftClient,
        renderer: GameRendererAccess,
        tickDelta: Float,
        state: WorldFrameState,
    ) {
        renderer.invokeSetupCamera(tickDelta, state.anaglyphFilter)

        state.terrainProjection.set(MatrixState.projection())
        state.view.set(MatrixState.modelView())

        val fov = renderer.invokeGetFov(tickDelta, true).toDouble()
        val aspect = client.width.toDouble() / client.height.toDouble()
        val viewDistance = renderer.getViewDistance()

        state.skyProjection.set(perspective(fov, aspect, viewDistance * SKY_FAR_SCALE))
        state.cloudProjection.set(perspective(fov, aspect, viewDistance * CLOUD_FAR_SCALE))

        MatrixState.matrixMode(GL_MODELVIEW)

        extractFrustum(state)
    }

    private fun extractFrustum(state: WorldFrameState) {
        viewProjection.set(state.terrainProjection).mul(state.view)
        state.frustum.set(viewProjection)

        inverseView.set(state.view).invert()
        frustumOffset.set(0f, 0f, 0f)
        inverseView.transformPosition(frustumOffset)

        state.frustumOriginX = state.cameraX + frustumOffset.x
        state.frustumOriginY = state.cameraY + frustumOffset.y
        state.frustumOriginZ = state.cameraZ + frustumOffset.z
    }

    private fun perspective(fov: Double, aspect: Double, far: Float): Matrix4f {
        MatrixState.matrixMode(GL_PROJECTION)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()
        MatrixState.perspective(fov, aspect, NEAR_PLANE, far.toDouble())
        val result = Matrix4f(MatrixState.projection())
        MatrixState.popMatrix()
        MatrixState.matrixMode(GL_MODELVIEW)
        return result
    }

    private fun extractFog(renderer: GameRendererAccess, tickDelta: Float, state: WorldFrameState) {
        renderer.invokeUpdateFog(tickDelta)

        renderer.invokeRenderFog(-1, tickDelta)
        state.skyFog.capture()

        renderer.invokeRenderFog(0, tickDelta)
        state.worldFog.capture()
    }

    private fun extractSky(client: MinecraftClient, tickDelta: Float, state: WorldFrameState) {
        val world = client.world ?: return
        val camera = client.cameraEntity ?: return

        state.skyEnabled = client.options.viewDistance >= MINIMUM_SKY_VIEW_DISTANCE
        state.endSky = world.dimension.type == 1
        state.hasSky = world.dimension.canPlayersSleep()
        state.hasGround = world.dimension.hasGround()

        if (!state.skyEnabled) {
            return
        }

        val skyColor = world.method_3631(camera, tickDelta)
        scratch[0] = skyColor.x.toFloat()
        scratch[1] = skyColor.y.toFloat()
        scratch[2] = skyColor.z.toFloat()
        state.applyAnaglyph(scratch)
        state.skyRed = scratch[0]
        state.skyGreen = scratch[1]
        state.skyBlue = scratch[2]

        state.skyAngle = world.getSkyAngle(tickDelta)
        state.skyAngleSin = MathHelper.sin(world.getSkyAngleRadians(tickDelta))
        state.rainGradient = world.getRainGradient(tickDelta)
        state.starBrightness = world.method_3707(tickDelta)
        state.moonPhase = world.moonPhase

        state.sunriseColor = world.dimension.getBackgroundColor(state.skyAngle, tickDelta)?.copyOf()

        val eye = client.player?.getCameraPosVec(tickDelta)
        state.eyeY = eye?.y ?: state.cameraY
        state.voidOffset = state.eyeY - world.horizonHeight
    }

    private fun extractClouds(client: MinecraftClient, tickDelta: Float, state: WorldFrameState) {
        val world = client.world ?: return
        val camera = client.cameraEntity ?: return

        state.cloudMode = if (state.hasSky) client.options.getCloudMode() else 0
        if (state.cloudMode == 0) {
            return
        }

        state.cloudsAboveTranslucent = camera.y + camera.eyeHeight >= CLOUD_LAYER_Y

        val cloudColor = world.getCloudColor(tickDelta)
        scratch[0] = cloudColor.x.toFloat()
        scratch[1] = cloudColor.y.toFloat()
        scratch[2] = cloudColor.z.toFloat()
        state.applyAnaglyph(scratch)
        state.cloudRed = scratch[0]
        state.cloudGreen = scratch[1]
        state.cloudBlue = scratch[2]

        val cameraY = (camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta).toFloat()
        state.cloudHeight = world.dimension.cloudHeight - cameraY + 0.33f

        val ticks = (client.worldRenderer as? WorldRendererAccess)?.getTicks() ?: 0
        val scrollX = camera.prevX + (camera.x - camera.prevX) * tickDelta +
                (ticks + tickDelta).toDouble() * 0.03
        val scrollZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta

        state.cloudScrollX = wrap(scrollX)
        state.cloudScrollZ = wrap(scrollZ)
    }

    private fun extractOverlays(
        client: MinecraftClient,
        renderer: GameRendererAccess,
        tickDelta: Float,
        state: WorldFrameState,
    ) {
        val world = client.world ?: return
        val camera = client.cameraEntity ?: return

        state.weatherVisible = world.getRainGradient(tickDelta) > 0f
        state.handVisible = renderer.isHandEnabled && !renderer.isRenderingPanorama

        val border = world.worldBorder
        val reach = (client.options.viewDistance * 16).toDouble()
        state.borderVisible = !(
            camera.x < border.boundEast - reach &&
                camera.x > border.boundWest + reach &&
                camera.z < border.boundSouth - reach &&
                camera.z > border.boundNorth + reach
            )
    }

    private fun wrap(value: Double): Double = value - floor(value / CLOUD_WRAP) * CLOUD_WRAP
}

package re.lilith.kalia.frame.graph.sky

import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.util.Identifier
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlEnums
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import kotlin.math.floor

/**
 * Draws the baked cloud meshes
 */
object CloudRenderer {
    private val CLOUDS = Identifier("textures/environment/clouds.png")

    private const val FANCY_SCALE = 12.0
    private const val FANCY_UV_SCALE = 0.00390625f
    private const val WRAP = 2048.0

    private var restoreMode = 0
    private var restoreUnit = 0

    fun render(tickDelta: Float, anaglyphFilter: Int, ticks: Int) {
        if (!CloudMesh.ensureBuilt()) {
            return
        }
        val client = MinecraftClient.getInstance()
        val camera = client.cameraEntity ?: return
        val world = client.world ?: return

        val height = layerHeight(world, camera, tickDelta)
        var scrollX = (scrolledX(camera, tickDelta, ticks)) / FANCY_SCALE
        var scrollZ = (scrolledZ(camera, tickDelta)) / FANCY_SCALE + 0.33
        scrollX = wrap(scrollX)
        scrollZ = wrap(scrollZ)

        beginLayer(client, anaglyphFilter, world, tickDelta)
        GlStateManager.scale(FANCY_SCALE.toFloat(), 1.0f, FANCY_SCALE.toFloat())

        // Vanilla keeps the integer part in the texture offset and the fraction in the geometry
        pushTransform(
            texU = floor(scrollX).toFloat() * FANCY_UV_SCALE,
            texV = floor(scrollZ).toFloat() * FANCY_UV_SCALE,
            offsetX = -(scrollX - floor(scrollX)).toFloat(),
            offsetY = height,
            offsetZ = -(scrollZ - floor(scrollZ)).toFloat(),
        )

        for (pass in 0 until 2) {
            if (pass == 0) {
                GlStateManager.colorMask(false, false, false, false)
            } else {
                when (anaglyphFilter) {
                    0 -> GlStateManager.colorMask(false, true, true, true)
                    1 -> GlStateManager.colorMask(true, false, false, true)
                    else -> GlStateManager.colorMask(true, true, true, true)
                }
            }

            if (height > -5.0f) {
                draw(CloudMesh.bottomMesh)
            }
            if (height <= 5.0f) {
                draw(CloudMesh.topMesh)
            }
            draw(CloudMesh.sideMesh)
        }

        popTransform()
        endLayer()
    }

    fun renderFast(tickDelta: Float, anaglyphFilter: Int, ticks: Int) {
        if (!CloudMesh.ensureFlatBuilt()) {
            return
        }
        val client = MinecraftClient.getInstance()
        val camera = client.cameraEntity ?: return
        val world = client.world ?: return

        val height = layerHeight(world, camera, tickDelta)
        val scrollX = wrap(scrolledX(camera, tickDelta, ticks))
        val scrollZ = wrap(scrolledZ(camera, tickDelta))

        beginLayer(client, anaglyphFilter, world, tickDelta)

        pushTransform(
            texU = (scrollX * CloudMesh.FLAT_UV_SCALE).toFloat(),
            texV = (scrollZ * CloudMesh.FLAT_UV_SCALE).toFloat(),
            offsetX = 0f,
            offsetY = height,
            offsetZ = 0f,
        )

        draw(CloudMesh.flatMesh)

        popTransform()
        endLayer()
    }

    private fun layerHeight(world: net.minecraft.client.world.ClientWorld, camera: Entity, tickDelta: Float): Float {
        val cameraY = (camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta).toFloat()
        return world.dimension.cloudHeight - cameraY + 0.33f
    }

    private fun scrolledX(camera: Entity, tickDelta: Float, ticks: Int): Double =
        camera.prevX + (camera.x - camera.prevX) * tickDelta + (ticks.toFloat() + tickDelta).toDouble() * 0.03

    private fun scrolledZ(camera: Entity, tickDelta: Float): Double =
        camera.prevZ + (camera.z - camera.prevZ) * tickDelta

    private fun wrap(value: Double): Double = value - floor(value / WRAP) * WRAP

    private fun beginLayer(
        client: MinecraftClient,
        anaglyphFilter: Int,
        world: net.minecraft.client.world.ClientWorld,
        tickDelta: Float,
    ) {
        GlStateManager.disableCull()
        client.textureManager.bindTexture(CLOUDS)
        GlStateManager.enableBlend()
        GlStateManager.blendFuncSeparate(770, 771, 1, 0)

        val cloudColor = world.getCloudColor(tickDelta)
        var red = cloudColor.x.toFloat()
        var green = cloudColor.y.toFloat()
        var blue = cloudColor.z.toFloat()
        if (anaglyphFilter != 2) {
            val luminance = (red * 30.0f + green * 59.0f + blue * 11.0f) / 100.0f
            val yellow = (red * 30.0f + green * 70.0f) / 100.0f
            val magenta = (red * 30.0f + blue * 70.0f) / 100.0f
            red = luminance
            green = yellow
            blue = magenta
        }
        GlStateManager.color(red, green, blue, 1.0f)
    }

    private fun endLayer() {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        GlStateManager.disableBlend()
        GlStateManager.enableCull()
    }

    private fun pushTransform(texU: Float, texV: Float, offsetX: Float, offsetY: Float, offsetZ: Float) {
        // MatrixState.flush publishes texture stack 0, but the GL_TEXTURE stack is selected by the
        // active unit, so pin it to 0 or the scroll would silently never reach the shader
        restoreMode = MatrixState.matrixMode()
        restoreUnit = MatrixState.activeTextureUnit
        MatrixState.activeTextureUnit = 0
        MatrixState.matrixMode(GlEnums.GL_TEXTURE)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()
        MatrixState.translate(texU, texV, 0f)
        MatrixState.matrixMode(restoreMode)

        ShaderUniforms.setModelOffset(offsetX, offsetY, offsetZ)
    }

    private fun popTransform() {
        ShaderUniforms.setModelOffset(0f, 0f, 0f)
        MatrixState.matrixMode(GlEnums.GL_TEXTURE)
        MatrixState.popMatrix()
        MatrixState.matrixMode(restoreMode)
        MatrixState.activeTextureUnit = restoreUnit
    }

    private fun draw(mesh: PersistentMesh?) {
        val buffer = mesh?.vertexBuffer ?: return
        val format = mesh.format ?: return
        KaliaDraw.drawResident(buffer, format, CloudMesh.GL_QUADS, mesh.vertexCount)
    }
}

package re.lilith.kalia.rendering.ui.pip

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen
import net.minecraft.entity.LivingEntity
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.draw.EntityBatchers
import re.lilith.kalia.gl.GlEnums.GL_MODELVIEW
import re.lilith.kalia.gl.GlEnums.GL_PROJECTION
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.ui.UI

object GuiEntityPreview {
    var isReplaying = false
        private set

    private var renderer: PictureInPictureRenderer<Request>? = null
    private var device: RenderDevice? = null
    private var scale = 0

    val isIdle get() = renderer?.isIdle ?: true

    val texture: GpuTexture? get() = renderer?.textureFor(KEY)

    val depth: GpuTexture? get() = renderer?.depthFor(KEY)

    fun beginFrame(device: RenderDevice, guiScale: Int) {
        val existing = renderer
        if (existing == null || this.device !== device || scale != guiScale) {
            existing?.close()
            renderer = PictureInPictureRenderer(device, "entity", ::draw)
            this.device = device
            scale = guiScale
        }
        renderer?.beginFrame()
    }

    fun capture(x: Int, y: Int, size: Int, mouseX: Float, mouseY: Float, entity: LivingEntity?): Boolean {
        val renderer = renderer ?: run {
            return false
        }
        if (isReplaying || entity == null || !UI.isRecording) {
            return false
        }

        val half = size * BOX_UNITS
        val x0 = x - half
        val y0 = y - half * 2f
        val extent = half * 2f

        renderer.submit(
            key = KEY,
            x = x0,
            y = y0,
            width = extent,
            height = extent * 2f,
            pixelWidth = (extent * scale).toInt().coerceAtLeast(1),
            pixelHeight = (extent * 2f * scale).toInt().coerceAtLeast(1),
            live = true,
            state = Request(x, y, size, mouseX, mouseY, entity, x0, y0),
        )
        return true
    }

    fun render(pass: PassContext) {
        val renderer = renderer ?: return
        renderer.render(pass)
    }

    private fun draw(pass: PassContext, request: Request) {
        GameFrame.record(pass) {
            isReplaying = true
            try {
                MatrixState.matrixMode(GL_PROJECTION)
                MatrixState.pushMatrix()
                MatrixState.loadIdentity()
                MatrixState.multiply(
                    projection.identity().setOrtho(
                        request.x0,
                        request.x0 + request.width,
                        request.y0 + request.height,
                        request.y0,
                        GUI_NEAR,
                        GUI_FAR,
                        true,
                    ),
                )

                MatrixState.matrixMode(GL_MODELVIEW)
                MatrixState.pushMatrix()
                MatrixState.loadIdentity()
                MatrixState.translate(0f, 0f, GUI_Z)
                MatrixState.flush()

                configureDispatcher()

                EntityBatchers.enterEntity()
                try {
                    SurvivalInventoryScreen.renderEntity(
                        request.x,
                        request.y,
                        request.size,
                        request.mouseX,
                        request.mouseY,
                        request.entity,
                    )
                } finally {
                    EntityBatchers.exitEntity()
                }
                EntityBatchers.flush()
            } catch (failure: Throwable) {
                KaliaMod.LOGGER.error("A GUI entity preview failed to render.", failure)
            } finally {
                isReplaying = false
                MatrixState.popMatrix()
                MatrixState.matrixMode(GL_PROJECTION)
                MatrixState.popMatrix()
                MatrixState.matrixMode(GL_MODELVIEW)
            }
        }
    }

    private fun configureDispatcher() {
        val client = MinecraftClient.getInstance() ?: return
        val dispatcher = runCatching { client.entityRenderManager }.getOrNull() ?: return
        if (dispatcher.world == null) {
            dispatcher.world = client.world
        }
        if (dispatcher.options == null) {
            dispatcher.options = client.options
        }
    }

    fun release() {
        renderer?.close()
        renderer = null
        device = null
        scale = 0
    }

    private val projection = org.joml.Matrix4f()

    private class Request(
        val x: Int,
        val y: Int,
        val size: Int,
        val mouseX: Float,
        val mouseY: Float,
        val entity: LivingEntity,
        val x0: Float,
        val y0: Float,
    ) {
        val width: Float get() = size * BOX_UNITS * 2f
        val height: Float get() = size * BOX_UNITS * 4f
    }

    private val KEY = Any()

    private const val BOX_UNITS = 1.6f

    private const val GUI_NEAR = 1000f
    private const val GUI_FAR = 3000f
    private const val GUI_Z = -(GUI_NEAR + GUI_FAR) / 2f
}

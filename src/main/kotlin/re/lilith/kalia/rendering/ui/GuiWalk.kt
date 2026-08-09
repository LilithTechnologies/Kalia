package re.lilith.kalia.rendering.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.Window
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview

/**
 * Runs the game's GUI code so that it submits, then closes submission
 *
 * @author Lunasa
 * @since 1.0.0
 */
object GuiWalk {
    fun collect(tickDelta: Float) {
        val client = MinecraftClient.getInstance() ?: return
        if (!KaliaEngine.ensureStarted()) {
            return
        }
        val device = KaliaEngine.device ?: return

        GuiPanorama.beginFrame()

        val window = Window(client)
        val scaledWidth = window.scaledWidth
        val scaledHeight = window.scaledHeight

        UI.begin(scaledWidth.toFloat(), scaledHeight.toFloat())
        GuiItems.beginFrame(device, window.scaleFactor)
        GuiEntityPreview.beginFrame(device, window.scaleFactor)

        MatrixState.matrixMode(GL11.GL_PROJECTION)
        MatrixState.loadIdentity()
        MatrixState.ortho(0.0, scaledWidth, scaledHeight, 0.0, GUI_NEAR, GUI_FAR)
        MatrixState.matrixMode(GL11.GL_MODELVIEW)
        MatrixState.loadIdentity()
        MatrixState.translate(0f, 0f, GUI_Z)

        if (client.world != null && !client.skipGameRender) {
            UI.group = GuiRenderState.GROUP_HUD
            runCatching { client.inGameHud.render(tickDelta) }
                .onFailure { failure -> logFailure("The in-game HUD", failure) }
        }

        val screen = client.currentScreen
        if (screen != null) {
            UI.group = GuiRenderState.GROUP_SCREEN
        }
        if (screen != null) {
            val mouseX = Mouse.getX() * scaledWidth.toInt() / client.width
            val mouseY = scaledHeight.toInt() - Mouse.getY() * scaledHeight.toInt() / client.height - 1
            runCatching { screen.render(mouseX, mouseY, tickDelta) }
                .onFailure { failure -> logFailure("A GUI screen", failure) }
        }
    }

    private const val GUI_NEAR = 1000.0
    private const val GUI_FAR = 3000.0
    private const val GUI_Z = -2000f

    private fun logFailure(what: String, failure: Throwable) {
        KaliaMod.LOGGER.error("{} failed while submitting.", what, failure)
    }
}

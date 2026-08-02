package re.lilith.kalia.platform

import net.minecraft.client.MinecraftClient
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.Display
import re.lilith.kalia.mixins.access.GameRendererAccess

object GameInput {
    private const val FOCUS_LOSS_GRACE_MILLIS = 500L
    private const val SENSITIVITY_BIAS = 0.2f
    private const val SENSITIVITY_SCALE = 0.6f
    private const val SENSITIVITY_GAIN = 8.0f

    fun update(tickDelta: Float) {
        val client = MinecraftClient.getInstance() ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        val active = Display.isActive()
        updateFocus(client, renderer, active)
        recentreOnMac(client, active)
        applyLook(client, renderer, tickDelta, active)
    }

    private fun updateFocus(client: MinecraftClient, renderer: GameRendererAccess, active: Boolean) {
        val pausable = client.options.pauseOnLostFocus &&
            (!client.options.touchscreen || !Mouse.isButtonDown(1))

        if (!active && pausable) {
            if (MinecraftClient.getTime() - renderer.lastWindowFocusedTime > FOCUS_LOSS_GRACE_MILLIS) {
                client.openGameMenuScreen()
            }
        } else {
            renderer.lastWindowFocusedTime = MinecraftClient.getTime()
        }
    }

    private fun recentreOnMac(client: MinecraftClient, active: Boolean) {
        if (!active || !MinecraftClient.IS_MAC || !client.focused || Mouse.isInsideWindow()) {
            return
        }
        Mouse.setGrabbed(false)
        Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2)
        Mouse.setGrabbed(true)
    }

    private fun applyLook(
        client: MinecraftClient,
        renderer: GameRendererAccess,
        tickDelta: Float,
        active: Boolean,
    ) {
        if (!client.focused || !active) {
            return
        }
        val player = client.player ?: return

        client.mouse.updateMouse()

        val sensitivity = client.options.sensitivity * SENSITIVITY_SCALE + SENSITIVITY_BIAS
        val gain = sensitivity * sensitivity * sensitivity * SENSITIVITY_GAIN
        var deltaX = client.mouse.x * gain
        var deltaY = client.mouse.y * gain
        val invert = if (client.options.invertYMouse) -1 else 1

        if (client.options.smoothCameraEnabled) {
            renderer.cursorDeltaX += deltaX
            renderer.cursorDeltaY += deltaY
            val elapsed = tickDelta - renderer.lastTickDelta
            renderer.lastTickDelta = tickDelta
            deltaX = renderer.smoothedCursorDeltaX * elapsed
            deltaY = renderer.smoothedCursorDeltaY * elapsed
        } else {
            renderer.cursorDeltaX = 0f
            renderer.cursorDeltaY = 0f
        }

        player.increaseTransforms(deltaX, deltaY * invert)
    }
}

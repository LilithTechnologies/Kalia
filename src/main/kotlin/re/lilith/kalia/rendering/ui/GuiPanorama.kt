package re.lilith.kalia.rendering.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.graph.ui.CubeMap
import re.lilith.kalia.frame.graph.ui.CubeMapTexture
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.renderer.command.PassContext

/**
 * The spinning title screen background.
 *
 * @author Lunasa
 * @since 1.0.0
 */
object GuiPanorama {
    private val TEXTURE = Identifier("kalia", "textures/gui/background/panorama")

    private var cubeMap: CubeMap? = null
    private var registered = false
    private var failed = false

    private var spin = 0f
    private var lastTime = System.nanoTime()

    var isRequested: Boolean = false
        private set

    fun beginFrame() {
        isRequested = false
        GuiBlur.enabled = false
    }

    fun request(spinning: Boolean) {
        if (failed) {
            return
        }

        val now = System.nanoTime()
        val deltaSeconds = (now - lastTime) / 1_000_000_000f
        lastTime = now
        if (spinning) {
            spin = MathHelper.wrapDegrees(spin + deltaSeconds * 20f * 0.1f)
        }

        isRequested = true

        GuiBlur.enabled = true
        GuiBlur.radius = BLUR_RADIUS
    }

    fun render(pass: PassContext) {
        if (!isRequested) {
            return
        }
        val map = resolve() ?: return
        runCatching { map.render(pass, PITCH, -spin) }
            .onFailure { failure ->
                KaliaMod.LOGGER.error("The panorama failed to draw and will be disabled.", failure)
                failed = true
            }
    }

    private fun resolve(): CubeMap? {
        cubeMap?.let { return it }
        if (KaliaEngine.device == null) {
            return null
        }
        return runCatching {
            if (!registered) {
                MinecraftClient.getInstance().textureManager.loadTexture(TEXTURE, CubeMapTexture(TEXTURE))
                registered = true
            }
            CubeMap(TEXTURE)
        }.onFailure { failure ->
            KaliaMod.LOGGER.error("The panorama could not be created and will be disabled.", failure)
            failed = true
        }.getOrNull()?.also { cubeMap = it }
    }

    private const val PITCH = 10f
    private const val BLUR_RADIUS = 12f
}

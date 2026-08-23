package re.lilith.kalia

import net.fabricmc.loader.api.FabricLoader
import org.embeddedt.embeddium.impl.gui.framework.TextComponent
import org.taumc.celeritas.api.IHooks
import re.lilith.kalia.frame.FrameAllocations
import re.lilith.kalia.platform.GameInput
import re.lilith.kalia.rendering.state.FrameState
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.GuiWalk
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings
import re.lilith.kalia.utility.ScreenshotUtility

object KaliaHooks  {
    @JvmStatic
    fun renderFrame() {
        FrameAllocations.begin()
        try {
            GameInput.update(FrameState.tickDelta)
            if (!KaliaEngine.beginFrame()) {
                return
            }
            GuiBackgroundBlur.enabled = false
            WorldFrame.collect(FrameState.tickDelta)
            GuiWalk.collect(FrameState.tickDelta)
            WorldFrameTimings.end(WorldFrameTimings.GUI_WALK)
            KaliaEngine.renderFrame()
            WorldFrameTimings.end(WorldFrameTimings.DEVICE_RENDER)
            ScreenshotUtility.processScreenshots()
        } finally {
            FrameAllocations.end()
        }
    }

    @JvmStatic
    fun setFrameState(
        tickDelta: Float,
        limitTime: Long
    ) {
        FrameState.let { frameState ->
            frameState.tickDelta = tickDelta
            frameState.limitTime = limitTime
        }
    }

    @JvmStatic
    fun isActive(): Boolean = KaliaEngine.isActive

    @JvmStatic
    fun shutdown() {
        KaliaEngine.shutdown()
    }

    @JvmStatic
    fun setVsync(enabled: Boolean) {
        KaliaEngine.settings = KaliaEngine.settings.copy(vsync = enabled)
    }
}

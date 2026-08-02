package re.lilith.kalia

import re.lilith.kalia.platform.GameInput
import re.lilith.kalia.rendering.state.FrameState
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.GuiWalk
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings

object KaliaHooks {
    @JvmStatic
    fun renderFrame() {
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

package re.lilith.kalia

import re.lilith.kalia.frame.FrameAllocations
import re.lilith.kalia.platform.GameInput
import re.lilith.kalia.frame.GameFrameShape
import re.lilith.kalia.frame.HostTimings
import re.lilith.kalia.frame.graph.EntityPoseStats
import re.lilith.kalia.frame.graph.entity.EntityStage
import re.lilith.kalia.frame.graph.occlusion.EntityOcclusion
import re.lilith.kalia.gl.FfpStats
import re.lilith.kalia.frame.graph.entity.nametag.NametagStage
import re.lilith.kalia.rendering.state.FrameState
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.GuiWalk
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings
import re.lilith.kalia.utility.ScreenshotUtility

object KaliaHooks  {
    @JvmStatic
    fun renderFrame() {
        WorldFrameTimings.markWall()
        HostTimings.beginFrame()
        EntityPoseStats.beginFrame()
        EntityStage.endFrame()
        NametagStage.endFrame()
        EntityOcclusion.endFrame()
        FfpStats.beginFrame()
        FrameAllocations.begin()
        try {
            GameInput.update(FrameState.tickDelta)
            if (!KaliaEngine.beginFrame()) {
                return
            }
            GuiBackgroundBlur.enabled = false
            WorldFrame.collect(FrameState.tickDelta)

            KaliaEngine.awaitRender()
            WorldFrameTimings.end(WorldFrameTimings.DEVICE_RENDER)

            WorldFrame.collectTerrain()
            if (KaliaEngine.lastFrameSkipped) {
                GuiItems.retryPending()
            }
            GuiWalk.collect(FrameState.tickDelta)
            WorldFrameTimings.end(WorldFrameTimings.GUI_WALK)

            WorldFrame.publish()
            UI.publish()
            GameFrameShape.capture()
            ScreenshotUtility.processScreenshots()
            KaliaEngine.submitFrame()
            KaliaEngine.awaitExclusiveRender()
        } finally {
            WorldFrameTimings.markWallEnd()
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
        KaliaEngine.terminate()
    }

    @JvmStatic
    fun setVsync(enabled: Boolean) {
        KaliaEngine.settings = KaliaEngine.settings.copy(vsync = enabled)
    }
}

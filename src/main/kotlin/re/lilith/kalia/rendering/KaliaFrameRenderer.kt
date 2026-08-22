package re.lilith.kalia.rendering

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.rendering.ui.GuiBlurPhase
import re.lilith.kalia.rendering.ui.GuiRenderState
import re.lilith.kalia.rendering.ui.UI

class KaliaFrameRenderer {
    fun renderUiBeforeBlur(pass: PassContext) {
        UI.prepare(pass.device)
        UI.draw(pass, GuiBlurPhase.BEFORE_BLUR)
    }

    fun renderUiAfterBlur(pass: PassContext) {
        UI.draw(pass, GuiBlurPhase.AFTER_BLUR)
    }

    fun renderUiBeforeBlurHud(pass: PassContext) {
        UI.prepare(pass.device)
        UI.drawGroup(pass, GuiBlurPhase.BEFORE_BLUR, GuiRenderState.GROUP_HUD)
    }

    fun renderUiBeforeBlurScreen(pass: PassContext) {
        UI.drawGroup(pass, GuiBlurPhase.BEFORE_BLUR, GuiRenderState.GROUP_SCREEN)
    }

    fun renderUiAfterBlurHud(pass: PassContext) {
        UI.drawGroup(pass, GuiBlurPhase.AFTER_BLUR, GuiRenderState.GROUP_HUD)
    }

    fun renderUiAfterBlurScreen(pass: PassContext) {
        UI.drawGroup(pass, GuiBlurPhase.AFTER_BLUR, GuiRenderState.GROUP_SCREEN)
    }

    fun renderUi(pass: PassContext) {
        UI.prepare(pass.device)
        UI.draw(pass, phase = null)
    }
}

package re.lilith.kalia.rendering

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.rendering.ui.GuiBlurPhase
import re.lilith.kalia.rendering.ui.UI

class KaliaFrameRenderer {
    fun renderUiBeforeBlur(pass: PassContext) {
        UI.prepare(pass.device)
        UI.draw(pass, GuiBlurPhase.BEFORE_BLUR)
    }

    fun renderUiAfterBlur(pass: PassContext) {
        UI.draw(pass, GuiBlurPhase.AFTER_BLUR)
    }

    fun renderUi(pass: PassContext) {
        UI.prepare(pass.device)
        UI.draw(pass, phase = null)
    }
}

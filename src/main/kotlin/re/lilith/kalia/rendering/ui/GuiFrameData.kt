package re.lilith.kalia.rendering.ui

class GuiFrameData {
    val state = GuiRenderState()
    val scissors = GuiScissorStack()
    val textures = GuiTextureRegistry()

    var width = 1f
    var height = 1f

    var layer = GuiLayer.SCREEN
    var phase = GuiBlurPhase.AFTER_BLUR
    var pinnedMaterial: GuiMaterial? = null

    var isRecording = false
    var prepared = false

    var lastElements = 0
    var lastItemElements = 0

    fun reset(guiWidth: Float, guiHeight: Float) {
        state.reset()
        scissors.reset()
        textures.reset()
        width = guiWidth
        height = guiHeight
        layer = GuiLayer.SCREEN
        phase = GuiBlurPhase.AFTER_BLUR
        pinnedMaterial = null
        isRecording = true
        prepared = false
    }
}

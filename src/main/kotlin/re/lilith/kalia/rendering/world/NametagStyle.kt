package re.lilith.kalia.rendering.world

object NametagStyle {
    const val DEFAULT_BACKGROUND: Int = 0x00000040

    var backgroundArgb: Int = DEFAULT_BACKGROUND

    var drawBackground: Boolean = true

    fun reset() {
        backgroundArgb = DEFAULT_BACKGROUND
        drawBackground = true
    }
}

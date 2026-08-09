package re.lilith.kalia.renderer.pipeline

data class ColorMask(
    val red: Boolean = true,
    val green: Boolean = true,
    val blue: Boolean = true,
    val alpha: Boolean = true,
) {
    companion object {
        val ALL: ColorMask = ColorMask()
        val NONE: ColorMask = ColorMask(red = false, green = false, blue = false, alpha = false)
    }
}
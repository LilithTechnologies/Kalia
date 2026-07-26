package re.lilith.kalia.renderer.geometry

data class Color(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) {
    companion object {
        val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
        val BLACK: Color = Color(0f, 0f, 0f, 1f)
        val WHITE: Color = Color(1f, 1f, 1f, 1f)

        fun argb(packed: Int): Color = Color(
            red = (packed ushr 16 and 0xFF) / 255f,
            green = (packed ushr 8 and 0xFF) / 255f,
            blue = (packed and 0xFF) / 255f,
            alpha = (packed ushr 24 and 0xFF) / 255f,
        )
    }
}

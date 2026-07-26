package re.lilith.kalia.renderer.geometry

/**
 * Represents a color using normalized floating-point RGBA components.
 *
 * @property red Red channel intensity.
 * @property green Green channel intensity.
 * @property blue Blue channel intensity.
 * @property alpha Alpha (opacity) channel intensity.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class Color(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    companion object {
        val TRANSPARENT = Color(0f, 0f, 0f, 0f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val WHITE = Color(1f, 1f, 1f, 1f)

        /**
         * Creates a color from a packed ARGB integer.
         *
         * @param packed Packed ARGB color value.
         * @return The decoded color.
         */
        fun argb(packed: Int) = Color(
            red = (packed ushr 16 and 0xFF) / 255f,
            green = (packed ushr 8 and 0xFF) / 255f,
            blue = (packed and 0xFF) / 255f,
            alpha = (packed ushr 24 and 0xFF) / 255f,
        )
    }
}
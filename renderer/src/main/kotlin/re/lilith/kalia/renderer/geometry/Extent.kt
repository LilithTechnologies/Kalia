package re.lilith.kalia.renderer.geometry

/**
 * Represents a two-dimensional size in pixels.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 */
data class Extent(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) {
            "Extent must be positive, got ${width}x$height."
        }
    }

    /**
     * Returns a new extent scaled by the supplied factor.
     *
     * @param factor Scale factor to apply.
     * @return The scaled extent.
     */
    fun scaled(factor: Float): Extent = Extent(
        width = (width * factor).toInt().coerceAtLeast(1),
        height = (height * factor).toInt().coerceAtLeast(1),
    )
}
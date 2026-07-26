package re.lilith.kalia.renderer.geometry

/**
 * Represents a rectangular region in pixel coordinates.
 *
 * @property x Horizontal position of the rectangle origin.
 * @property y Vertical position of the rectangle origin.
 * @property width Rectangle width in pixels.
 * @property height Rectangle height in pixels.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        /**
         * Creates a rectangle covering the entire extent.
         *
         * @param extent The extent to cover.
         * @return A rectangle matching the extent.
         */
        fun of(extent: Extent): Rect =
            Rect(0, 0, extent.width, extent.height)
    }
}
package re.lilith.kalia.renderer.geometry

/**
 * Defines the transformation from normalized device coordinates to a
 * rectangular region of a render target.
 *
 * @property x Horizontal position of the viewport origin in pixels.
 * @property y Vertical position of the viewport origin in pixels.
 * @property width Viewport width in pixels.
 * @property height Viewport height in pixels.
 * @property minDepth Minimum depth value after viewport transformation.
 * @property maxDepth Maximum depth value after viewport transformation.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class Viewport(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val minDepth: Float = 0f,
    val maxDepth: Float = 1f,
) {
    /**
     * Packed viewport bounds as `[x, y, width, height]`.
     */
    val array = intArrayOf(x, y, width, height)

    companion object {
        /**
         * Creates a viewport covering the entire extent. The default
         * depth range is used, being [0.0, 1.0]
         *
         * @param extent The target extent.
         * @return A viewport matching the extent.
         */
        fun of(extent: Extent): Viewport =
            Viewport(0, 0, extent.width, extent.height)
    }
}
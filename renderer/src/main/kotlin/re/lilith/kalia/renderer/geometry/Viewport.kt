package re.lilith.kalia.renderer.geometry

data class Viewport(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val minDepth: Float = 0f,
    val maxDepth: Float = 1f,
) {
    val array = intArrayOf(x, y, width, height)

    companion object {
        fun of(extent: Extent): Viewport = Viewport(0, 0, extent.width, extent.height)
    }
}

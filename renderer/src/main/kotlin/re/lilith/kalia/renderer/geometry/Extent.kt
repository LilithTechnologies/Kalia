package re.lilith.kalia.renderer.geometry

data class Extent(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Extent must be positive, got ${width}x$height." }
    }

    fun scaled(factor: Float): Extent = Extent(
        width = (width * factor).toInt().coerceAtLeast(1),
        height = (height * factor).toInt().coerceAtLeast(1),
    )
}

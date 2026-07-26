package re.lilith.kalia.renderer.geometry

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    companion object {
        fun of(extent: Extent): Rect = Rect(0, 0, extent.width, extent.height)
    }
}

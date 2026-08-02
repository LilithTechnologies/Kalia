package re.lilith.kalia.rendering.ui.text

class TextLayout(
    @JvmField val glyphs: FloatArray,
    @JvmField val count: Int,
    @JvmField val advance: Float,
) {
    companion object {
        const val FLOATS_PER_GLYPH = 11
        const val PAGE_DECORATION = -2
        const val PAGE_ASCII = -1
    }
}

package re.lilith.kalia.renderer.graph

/**
 * Opaque reference to a texture inside a [RenderGraph]
 */
@JvmInline
value class TextureHandle(val id: Int) {
    companion object {
        // The image presented to the screen
        val BACK_BUFFER = TextureHandle(0)
    }
}
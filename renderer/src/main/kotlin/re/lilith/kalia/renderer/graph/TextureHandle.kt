package re.lilith.kalia.renderer.graph

/**
 * Opaque reference to a texture within a [RenderGraph].
 *
 * @author Lunasa
 * @since 1.0.0
 */
@JvmInline
value class TextureHandle(val id: Int) {
    companion object {
        /**
         * The texture presented to the screen.
         */
        val BACK_BUFFER = TextureHandle(0)
    }
}

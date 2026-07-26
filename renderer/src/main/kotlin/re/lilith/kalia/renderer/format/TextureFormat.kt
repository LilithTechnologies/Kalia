package re.lilith.kalia.renderer.format

/**
 * Texture and render-target formats that the engine is able to guarantee
 */
enum class TextureFormat(
    val bytesPerPixel: Int,
    val aspect: FormatAspect,
) {
    R8(1, FormatAspect.COLOR),
    RG8(2, FormatAspect.COLOR),
    RGBA8(4, FormatAspect.COLOR),
    BGRA8(4, FormatAspect.COLOR),
    RGBA16F(8, FormatAspect.COLOR),
    RGBA32F(16, FormatAspect.COLOR),
    DEPTH32F(4, FormatAspect.DEPTH),
    DEPTH24_STENCIL8(4, FormatAspect.DEPTH_STENCIL),
    DEPTH32F_STENCIL8(8, FormatAspect.DEPTH_STENCIL),
    ;

    val isColor: Boolean get() = aspect == FormatAspect.COLOR
    val isDepth: Boolean get() = !isColor
    val hasStencil: Boolean get() = aspect == FormatAspect.DEPTH_STENCIL
}

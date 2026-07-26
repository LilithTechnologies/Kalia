package re.lilith.kalia.renderer.format

/**
 * A texture format defines the memory layout, precision, and intended usage
 * of texture data. Formats may represent color data, depth values, or
 * combined depth-stencil attachments.
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class TextureFormat(
    val bytesPerPixel: Int,
    val aspect: FormatAspect,
) {
    // im not writing docs for all ts
    // you know what it is

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

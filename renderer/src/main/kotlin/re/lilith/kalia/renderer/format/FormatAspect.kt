package re.lilith.kalia.renderer.format

/**
 * Describes which texture aspect is represented by a format.
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class FormatAspect {
    /**
     * A color-renderable or color-sampled format.
     */
    COLOR,

    /**
     * A format containing depth information only.
     */
    DEPTH,

    /**
     * A format containing both depth and stencil information.
     */
    DEPTH_STENCIL
    ;
}
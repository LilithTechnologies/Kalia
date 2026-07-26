package re.lilith.kalia.renderer.graph

/**
 * The action performed on an attachment's existing contents when a pass starts.
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class LoadOp {
    /**
     * Preserves the existing attachment contents.
     */
    LOAD,

    /**
     * Clears the attachment before rendering begins.
     */
    CLEAR,

    /**
     * Discards any previous attachment contents. The initial contents become undefined
     * and must not be relied upon.
     */
    DISCARD
    ;
}
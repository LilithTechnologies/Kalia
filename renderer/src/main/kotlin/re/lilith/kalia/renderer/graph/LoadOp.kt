package re.lilith.kalia.renderer.graph

/**
 * What happens to an attachment's existing contents when a pass start
 */
enum class LoadOp {
    LOAD,
    CLEAR,
    DISCARD
    ;
}
package re.lilith.kalia.renderer.graph

/**
 * Describes a depth attachment used by a render pass.
 *
 * @property target The texture used as the depth attachment.
 * @property loadOp Operation performed on the attachment contents when the pass begins.
 * @property clearDepth Depth value used when [loadOp] performs a clear.
 * @property clearStencil Stencil value used when [loadOp] performs a clear on stencil-capable formats.
 * @property write Whether depth writes are enabled. When `false`, the
 * attachment is treated as read-only, allowing it to be sampled by other
 * passes while still participating in depth tests.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class DepthAttachment internal constructor(
    /**
     * The texture used as the depth attachment.
     */
    val target: TextureHandle,

    /**
     * Defines how existing attachment contents are handled at the start of the pass.
     */
    val loadOp: LoadOp,

    /**
     * Depth value applied when the attachment is cleared.
     */
    val clearDepth: Float,

    /**
     * Stencil value applied when the attachment is cleared.
     */
    val clearStencil: Int,

    /**
     * Whether depth writes are enabled for the pass.
     *
     * When `false`, the attachment remains readable by shaders.
     */
    val write: Boolean,
)
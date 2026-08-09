package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.geometry.Color

/**
 * Describes a color attachment used by a render pass.
 *
 * @property target The texture written by the attachment.
 * @property loadOp Operation performed on the attachment contents when the pass begins.
 * @property clearColor Clear value used when [loadOp] requests a color clear.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class ColorAttachment internal constructor(
    /**
     * The texture written by this color attachment.
     */
    val target: TextureHandle,

    /**
     * Defines how existing attachment contents are handled at the start of the pass.
     */
    val loadOp: LoadOp,

    /**
     * Clear color applied when the load operation performs a clear.
     */
    val clearColor: Color,
)
package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.command.PassContext

/**
 * A single executable pass within a render graph.
 *
 * @property name Human-readable pass name used for debugging and profiling.
 * @property colorAttachments Color attachments written by the pass.
 * @property depthAttachment Optional depth attachment used by the pass.
 * @property sampledInputs Textures sampled by the pass.
 * @property hasSideEffects Whether the pass performs externally observable
 * work that must not be optimized away, even if its outputs are otherwise
 * unused.
 * @property enabled Predicate evaluated during graph compilation to determine whether the pass should execute.
 * @property body Commands recorded when the pass executes.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class GraphPass internal constructor(
    /**
     * Human-readable pass name used for debugging and profiling tools.
     */
    val name: String,

    /**
     * Color attachments written by the pass.
     */
    val colorAttachments: List<ColorAttachment>,

    /**
     * Optional depth attachment used during rendering.
     */
    val depthAttachment: DepthAttachment?,

    /**
     * Textures sampled by shaders during pass execution.
     */
    val sampledInputs: List<TextureHandle>,

    /**
     * Whether this pass performs externally observable work.
     */
    val hasSideEffects: Boolean,

    /**
     * Determines whether the pass should be included in graph execution.
     */
    val enabled: () -> Boolean,

    /**
     * Records rendering commands for the pass.
     */
    val body: PassContext.() -> Unit,
) {
    val writes: List<TextureHandle> =
        buildList {
            colorAttachments.forEach { add(it.target) }
            depthAttachment?.takeIf(DepthAttachment::write)?.let { add(it.target) }
        }
}
package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.command.PassContext

/**
 * One unit of work in the graph
 */
class GraphPass internal constructor(
    val name: String,
    val colorAttachments: List<ColorAttachment>,
    val depthAttachment: DepthAttachment?,
    val sampledInputs: List<TextureHandle>,
    val hasSideEffects: Boolean,
    val enabled: () -> Boolean,
    val body: PassContext.() -> Unit,
) {
    internal val writes: List<TextureHandle> =
        buildList {
            colorAttachments.forEach { add(it.target) }
            depthAttachment?.takeIf(DepthAttachment::write)?.let { add(it.target) }
        }
}
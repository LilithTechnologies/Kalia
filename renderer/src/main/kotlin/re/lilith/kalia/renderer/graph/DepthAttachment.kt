package re.lilith.kalia.renderer.graph

class DepthAttachment internal constructor(
    val target: TextureHandle,
    val loadOp: LoadOp,
    val clearDepth: Float,
    val clearStencil: Int,
    // False turns the depth attachment read-only, which lets other passes sample it
    val write: Boolean,
)
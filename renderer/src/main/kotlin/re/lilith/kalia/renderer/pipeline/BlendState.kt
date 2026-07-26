package re.lilith.kalia.renderer.pipeline

data class BlendState(
    val enabled: Boolean = false,
    val srcColor: BlendFactor = BlendFactor.ONE,
    val dstColor: BlendFactor = BlendFactor.ZERO,
    val colorOp: BlendOp = BlendOp.ADD,
    val srcAlpha: BlendFactor = BlendFactor.ONE,
    val dstAlpha: BlendFactor = BlendFactor.ZERO,
    val alphaOp: BlendOp = BlendOp.ADD,
    val logicOp: LogicOp? = null,
) {
    companion object {
        val OPAQUE: BlendState = BlendState()

        val ALPHA: BlendState = BlendState(
            enabled = true,
            srcColor = BlendFactor.SRC_ALPHA,
            dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA,
        )

        val ADDITIVE: BlendState = BlendState(
            enabled = true,
            srcColor = BlendFactor.SRC_ALPHA,
            dstColor = BlendFactor.ONE,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ONE,
        )
    }
}

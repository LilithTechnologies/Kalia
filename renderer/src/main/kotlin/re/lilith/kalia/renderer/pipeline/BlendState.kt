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
        @JvmField
        val OPAQUE: BlendState = BlendState()

        @JvmField
        val ALPHA: BlendState = BlendState(
            enabled = true,
            srcColor = BlendFactor.SRC_ALPHA,
            dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA,
        )

        /** For sources whose colour is already multiplied by alpha, such as Skia's N32Premul surfaces. */
        @JvmField
        val PREMULTIPLIED: BlendState = BlendState(
            enabled = true,
            srcColor = BlendFactor.ONE,
            dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA,
        )

        @JvmField
        val ADDITIVE: BlendState = BlendState(
            enabled = true,
            srcColor = BlendFactor.SRC_ALPHA,
            dstColor = BlendFactor.ONE,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ONE,
        )
    }
}

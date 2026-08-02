package re.lilith.kalia.rendering.ui

import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.pipeline.BlendFactor
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.LogicOp

/**
 * The fixed set of blend configurations GUI drawing uses.
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class GuiMaterial(val blend: BlendState) {
    TRANSLUCENT(BlendState.ALPHA),

    OPAQUE(BlendState.OPAQUE),

    ADDITIVE(BlendState.ADDITIVE),

    INVERT(
        BlendState(
            enabled = true,
            srcColor = BlendFactor.ONE_MINUS_DST_COLOR,
            dstColor = BlendFactor.ONE_MINUS_SRC_COLOR,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ZERO,
        ),
    ),

    MULTIPLY_INVERSE(
        BlendState(
            enabled = true,
            srcColor = BlendFactor.ZERO,
            dstColor = BlendFactor.ONE_MINUS_SRC_COLOR,
            srcAlpha = BlendFactor.ONE,
            dstAlpha = BlendFactor.ZERO,
        ),
    ),

    LOGIC_OR_REVERSE(
        BlendState(enabled = false, logicOp = LogicOp.OR_REVERSE),
    ),
    ;

    companion object {
        @JvmField
        val VALUES = entries.toTypedArray()

        fun current(): GuiMaterial {
            val blend = GlState.blendState()

            if (blend.logicOp == LogicOp.OR_REVERSE) {
                return LOGIC_OR_REVERSE
            }

            if (!blend.enabled) {
                return OPAQUE
            }
            return when {
                blend.srcColor == BlendFactor.ONE_MINUS_DST_COLOR &&
                        blend.dstColor == BlendFactor.ONE_MINUS_SRC_COLOR -> INVERT

                blend.dstColor == BlendFactor.ONE &&
                        (blend.srcColor == BlendFactor.SRC_ALPHA || blend.srcColor == BlendFactor.ONE) -> ADDITIVE

                else -> TRANSLUCENT
            }
        }
    }
}

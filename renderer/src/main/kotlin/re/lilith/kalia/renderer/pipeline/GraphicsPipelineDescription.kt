package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.shader.ShaderProgram

data class GraphicsPipelineDescription @JvmOverloads constructor(
    val program: ShaderProgram,
    val vertexFormat: VertexFormat?,
    val attachments: AttachmentLayout,
    val raster: RasterState = RasterState(),
    val depth: DepthState = DepthState.DISABLED,
    val blend: BlendState = BlendState.OPAQUE,
    val colorMask: ColorMask = ColorMask.ALL,
    val instanceFormat: VertexFormat? = null,
) {
    init {
        require(attachments.depthFormat != null || !depth.test) {
            "Pipeline '${program.label}' enables depth testing but the pass has no depth attachment."
        }
        require(instanceFormat == null || instanceFormat.stepMode == VertexStepMode.INSTANCE) {
            "Pipeline '${program.label}' supplies an instanceFormat whose step mode is not INSTANCE."
        }
    }

    private val cachedHashCode: Int = run {
        var result = program.hashCode()
        result = 31 * result + (vertexFormat?.hashCode() ?: 0)
        result = 31 * result + attachments.hashCode()
        result = 31 * result + raster.hashCode()
        result = 31 * result + depth.hashCode()
        result = 31 * result + blend.hashCode()
        result = 31 * result + colorMask.hashCode()
        result = 31 * result + (instanceFormat?.hashCode() ?: 0)
        result
    }

    override fun hashCode(): Int = cachedHashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GraphicsPipelineDescription

        if (cachedHashCode != other.cachedHashCode) return false
        if (program != other.program) return false
        if (vertexFormat != other.vertexFormat) return false
        if (attachments != other.attachments) return false
        if (raster != other.raster) return false
        if (depth != other.depth) return false
        if (blend != other.blend) return false
        if (colorMask != other.colorMask) return false
        if (instanceFormat != other.instanceFormat) return false

        return true
    }
}

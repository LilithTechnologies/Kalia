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
}

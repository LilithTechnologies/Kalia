package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.shader.ShaderProgram

data class GraphicsPipelineDescription(
    val program: ShaderProgram,
    val vertexFormat: VertexFormat?,
    val attachments: AttachmentLayout,
    val raster: RasterState = RasterState(),
    val depth: DepthState = DepthState.DISABLED,
    val blend: BlendState = BlendState.OPAQUE,
    val colorMask: ColorMask = ColorMask.ALL,
) {
    init {
        require(attachments.depthFormat != null || !depth.test) {
            "Pipeline '${program.label}' enables depth testing but the pass has no depth attachment."
        }
    }
}

package re.lilith.vulkan.api.pipeline

data class GraphicsPipelineConfig(
    val shaders: List<ShaderModule>,
    val layout: PipelineLayout,
    val rendering: PipelineRendering,
    val cache: PipelineCache? = null,
    val shaderSpecializations: Map<ShaderModule, SpecializationInfo> = emptyMap(),
    val vertexInput: VertexInputState = VertexInputState(),
    val topology: PrimitiveTopology = PrimitiveTopology.TriangleList,
    val primitiveRestartEnable: Boolean = false,
    val viewportState: ViewportState = ViewportState(),
    val rasterization: RasterizationState = RasterizationState(),
    val multisampling: MultisampleState = MultisampleState(),
    val depthStencil: DepthStencilState? = null,
    val colorBlend: ColorBlendState = ColorBlendState(),
    val dynamicStates: List<DynamicState> = listOf(DynamicState.Viewport, DynamicState.Scissor),
)
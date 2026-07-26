package re.lilith.vulkan.api.pipeline

data class RasterizationState(
    val polygonMode: PolygonMode = PolygonMode.Fill,
    val cullMode: CullMode = CullMode.Back,
    val frontFace: FrontFace = FrontFace.CounterClockwise,
    val lineWidth: Float = 1f,
    val depthClampEnable: Boolean = false,
    val rasterizerDiscardEnable: Boolean = false,
    val depthBiasEnable: Boolean = false,
    val depthBiasConstantFactor: Float = 0f,
    val depthBiasClamp: Float = 0f,
    val depthBiasSlopeFactor: Float = 0f,
) {
    init {
        require(lineWidth > 0f) { "lineWidth must be > 0." }
    }
}

package re.lilith.vulkan.api.pipeline

data class DepthStencilState(
    val depthTestEnable: Boolean = false,
    val depthWriteEnable: Boolean = false,
    val depthCompareOperation: CompareOperation = CompareOperation.LessOrEqual,
    val depthBoundsTestEnable: Boolean = false,
    val stencilTestEnable: Boolean = false,
    val front: StencilOperationState = StencilOperationState(),
    val back: StencilOperationState = StencilOperationState(),
    val minDepthBounds: Float = 0f,
    val maxDepthBounds: Float = 1f,
) {
    init {
        require(minDepthBounds <= maxDepthBounds) { "minDepthBounds must be <= maxDepthBounds." }
    }
}


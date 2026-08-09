package re.lilith.vulkan.api.types.clear

data class ClearColorValue(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
)

data class ClearDepthStencilValue(
    val depth: Float = 1f,
    val stencil: Int = 0,
)

sealed interface ClearValue {
    data class Color(val value: ClearColorValue) : ClearValue
    data class DepthStencil(val value: ClearDepthStencilValue) : ClearValue
}


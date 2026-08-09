package re.lilith.vulkan.api.presentation

data class AcquiredSwapchainImage(
    val index: Int,
    val image: SwapchainImage,
    val suboptimal: Boolean = false,
)

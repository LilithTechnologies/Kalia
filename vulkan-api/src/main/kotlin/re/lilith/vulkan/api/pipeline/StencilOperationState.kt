package re.lilith.vulkan.api.pipeline

data class StencilOperationState(
    val failOperation: StencilOperation = StencilOperation.Keep,
    val passOperation: StencilOperation = StencilOperation.Keep,
    val depthFailOperation: StencilOperation = StencilOperation.Keep,
    val compareOperation: CompareOperation = CompareOperation.Always,
    val compareMask: Int = 0,
    val writeMask: Int = 0,
    val reference: Int = 0,
)


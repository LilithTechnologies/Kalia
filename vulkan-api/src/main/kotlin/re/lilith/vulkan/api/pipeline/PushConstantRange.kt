package re.lilith.vulkan.api.pipeline

data class PushConstantRange(
    val offset: Int,
    val size: Int,
    val stageFlags: ShaderStageFlags,
) {
    init {
        require(offset >= 0) { "offset must be >= 0." }
        require(size > 0) { "size must be > 0." }
    }
}

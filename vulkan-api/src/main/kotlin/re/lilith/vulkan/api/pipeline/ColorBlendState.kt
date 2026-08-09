package re.lilith.vulkan.api.pipeline

data class ColorBlendState(
    val attachments: List<ColorBlendAttachmentState> = emptyList(),
    val logicOperationEnable: Boolean = false,
    val logicOperation: LogicOperation = LogicOperation.Copy,
    val blendConstants: List<Float> = listOf(0f, 0f, 0f, 0f),
) {
    init {
        require(blendConstants.size == 4) { "blendConstants must contain exactly 4 values." }
    }
}


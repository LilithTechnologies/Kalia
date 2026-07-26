package re.lilith.vulkan.api.pipeline

data class ColorBlendAttachmentState(
    val blendEnable: Boolean = false,
    val sourceColorBlendFactor: BlendFactor = BlendFactor.One,
    val destinationColorBlendFactor: BlendFactor = BlendFactor.Zero,
    val colorBlendOperation: BlendOperation = BlendOperation.Add,
    val sourceAlphaBlendFactor: BlendFactor = BlendFactor.One,
    val destinationAlphaBlendFactor: BlendFactor = BlendFactor.Zero,
    val alphaBlendOperation: BlendOperation = BlendOperation.Add,
    val colorWriteMask: ColorComponentMask = ColorComponentMask.All,
)


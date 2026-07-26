package re.lilith.vulkan.api.pipeline

data class ComputePipelineConfig(
    val shader: ShaderModule,
    val layout: PipelineLayout,
    val cache: PipelineCache? = null,
    val specialization: SpecializationInfo? = null,
)


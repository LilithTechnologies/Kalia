package re.lilith.vulkan.api.pipeline

class ShaderModuleInfo(
    val stage: ShaderStage,
    val entryPoint: String,
    val spirv: ByteArray,
)

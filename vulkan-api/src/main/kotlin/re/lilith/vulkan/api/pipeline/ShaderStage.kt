package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class ShaderStage(internal val vkValue: Int) {
    Vertex(VK10.VK_SHADER_STAGE_VERTEX_BIT),
    TessellationControl(VK10.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT),
    TessellationEvaluation(VK10.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT),
    Geometry(VK10.VK_SHADER_STAGE_GEOMETRY_BIT),
    Fragment(VK10.VK_SHADER_STAGE_FRAGMENT_BIT),
    Compute(VK10.VK_SHADER_STAGE_COMPUTE_BIT),
}

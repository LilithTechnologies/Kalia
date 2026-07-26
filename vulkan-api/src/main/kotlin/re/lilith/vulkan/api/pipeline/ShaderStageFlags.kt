package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

@JvmInline
value class ShaderStageFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: ShaderStageFlags): ShaderStageFlags = ShaderStageFlags(vkBits or other.vkBits)

    companion object {
        val None = ShaderStageFlags(0)
        val Vertex = ShaderStageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
        val TessellationControl = ShaderStageFlags(VK10.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT)
        val TessellationEvaluation = ShaderStageFlags(VK10.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT)
        val Geometry = ShaderStageFlags(VK10.VK_SHADER_STAGE_GEOMETRY_BIT)
        val Fragment = ShaderStageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
        val Compute = ShaderStageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
        val AllGraphics = ShaderStageFlags(VK10.VK_SHADER_STAGE_ALL_GRAPHICS)
        val All = ShaderStageFlags(VK10.VK_SHADER_STAGE_ALL)
    }
}
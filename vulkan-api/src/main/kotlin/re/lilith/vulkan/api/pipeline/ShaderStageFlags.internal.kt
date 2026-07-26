package re.lilith.vulkan.api.pipeline

internal val ShaderStage.flags: ShaderStageFlags
    get() = when (this) {
        ShaderStage.Vertex -> ShaderStageFlags.Vertex
        ShaderStage.TessellationControl -> ShaderStageFlags.TessellationControl
        ShaderStage.TessellationEvaluation -> ShaderStageFlags.TessellationEvaluation
        ShaderStage.Geometry -> ShaderStageFlags.Geometry
        ShaderStage.Fragment -> ShaderStageFlags.Fragment
        ShaderStage.Compute -> ShaderStageFlags.Compute
    }
package re.lilith.vulkan.api.types.flags

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class PipelineStageMask internal constructor(internal val vkBits: Int) {
    operator fun plus(other: PipelineStageMask): PipelineStageMask = PipelineStageMask(vkBits or other.vkBits)

    companion object {
        val None = PipelineStageMask(VulkanConstants.PipelineStages.none)
        val TopOfPipe = PipelineStageMask(VulkanConstants.PipelineStages.topOfPipe)
        val DrawIndirect = PipelineStageMask(VulkanConstants.PipelineStages.drawIndirect)
        val VertexInput = PipelineStageMask(VulkanConstants.PipelineStages.vertexInput)
        val VertexShader = PipelineStageMask(VulkanConstants.PipelineStages.vertexShader)
        val TessellationControlShader = PipelineStageMask(VulkanConstants.PipelineStages.tessellationControlShader)
        val TessellationEvaluationShader =
            PipelineStageMask(VulkanConstants.PipelineStages.tessellationEvaluationShader)
        val GeometryShader = PipelineStageMask(VulkanConstants.PipelineStages.geometryShader)
        val FragmentShader = PipelineStageMask(VulkanConstants.PipelineStages.fragmentShader)
        val EarlyFragmentTests = PipelineStageMask(VulkanConstants.PipelineStages.earlyFragmentTests)
        val LateFragmentTests = PipelineStageMask(VulkanConstants.PipelineStages.lateFragmentTests)
        val ColorAttachmentOutput = PipelineStageMask(VulkanConstants.PipelineStages.colorAttachmentOutput)
        val ComputeShader = PipelineStageMask(VulkanConstants.PipelineStages.computeShader)
        val Transfer = PipelineStageMask(VulkanConstants.PipelineStages.transfer)
        val BottomOfPipe = PipelineStageMask(VulkanConstants.PipelineStages.bottomOfPipe)
        val Host = PipelineStageMask(VulkanConstants.PipelineStages.host)
        val AllGraphics = PipelineStageMask(VulkanConstants.PipelineStages.allGraphics)
        val AllCommands = PipelineStageMask(VulkanConstants.PipelineStages.allCommands)
    }
}

@JvmInline
value class AccessMask internal constructor(internal val vkBits: Int) {
    operator fun plus(other: AccessMask): AccessMask = AccessMask(vkBits or other.vkBits)

    companion object {
        val None = AccessMask(VulkanConstants.AccessMasks.none)
        val IndirectCommandRead = AccessMask(VulkanConstants.AccessMasks.indirectCommandRead)
        val IndexRead = AccessMask(VulkanConstants.AccessMasks.indexRead)
        val VertexAttributeRead = AccessMask(VulkanConstants.AccessMasks.vertexAttributeRead)
        val UniformRead = AccessMask(VulkanConstants.AccessMasks.uniformRead)
        val InputAttachmentRead = AccessMask(VulkanConstants.AccessMasks.inputAttachmentRead)
        val ShaderRead = AccessMask(VulkanConstants.AccessMasks.shaderRead)
        val ShaderWrite = AccessMask(VulkanConstants.AccessMasks.shaderWrite)
        val ColorAttachmentRead = AccessMask(VulkanConstants.AccessMasks.colorAttachmentRead)
        val ColorAttachmentWrite = AccessMask(VulkanConstants.AccessMasks.colorAttachmentWrite)
        val DepthStencilAttachmentRead = AccessMask(VulkanConstants.AccessMasks.depthStencilAttachmentRead)
        val DepthStencilAttachmentWrite = AccessMask(VulkanConstants.AccessMasks.depthStencilAttachmentWrite)
        val TransferRead = AccessMask(VulkanConstants.AccessMasks.transferRead)
        val TransferWrite = AccessMask(VulkanConstants.AccessMasks.transferWrite)
        val HostRead = AccessMask(VulkanConstants.AccessMasks.hostRead)
        val HostWrite = AccessMask(VulkanConstants.AccessMasks.hostWrite)
        val MemoryRead = AccessMask(VulkanConstants.AccessMasks.memoryRead)
        val MemoryWrite = AccessMask(VulkanConstants.AccessMasks.memoryWrite)
    }
}

@JvmInline
value class DependencyFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: DependencyFlags): DependencyFlags = DependencyFlags(vkBits or other.vkBits)

    companion object {
        val None = DependencyFlags(VulkanConstants.DependencyFlags.none)
        val ByRegion = DependencyFlags(VulkanConstants.DependencyFlags.byRegion)
        val ViewLocal = DependencyFlags(VulkanConstants.DependencyFlags.viewLocal)
        val DeviceGroup = DependencyFlags(VulkanConstants.DependencyFlags.deviceGroup)
    }
}

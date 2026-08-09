package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.vulkan.api.pipeline.GraphicsPipeline
import re.lilith.vulkan.api.pipeline.PipelineLayout
import re.lilith.vulkan.api.pipeline.ShaderModule
import re.lilith.vulkan.api.descriptor.DescriptorSetLayout as VkDescriptorSetLayout

internal class VulkanPipeline(
    private val owner: VulkanRenderDevice,
    override val label: String,
    val description: GraphicsPipelineDescription,
    val pipeline: GraphicsPipeline,
    val layout: PipelineLayout,
    val descriptorSetLayout: VkDescriptorSetLayout?,
    private val modules: List<ShaderModule>,
) : GpuPipeline {
    private var closed = false
    override val isClosed: Boolean get() = closed

    val bindings get() = description.program.bindings

    val pushConstantBytes: Int get() = description.program.pushConstantBytes

    override fun close() {
        if (closed) return
        closed = true
        owner.scheduleRelease(pipeline)
        owner.scheduleRelease(layout)
        descriptorSetLayout?.let(owner::scheduleRelease)
        modules.forEach(owner::scheduleRelease)
    }
}
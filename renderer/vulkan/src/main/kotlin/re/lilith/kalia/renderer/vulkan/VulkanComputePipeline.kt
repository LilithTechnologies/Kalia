package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.pipeline.ComputePipelineDescription
import re.lilith.kalia.renderer.resource.GpuComputePipeline
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.debug.DebugNames
import re.lilith.vulkan.api.descriptor.DescriptorSetLayout
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutBinding
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutConfig
import re.lilith.vulkan.api.pipeline.ComputePipelineConfig
import re.lilith.vulkan.api.pipeline.createPipelineLayout
import re.lilith.vulkan.api.pipeline.PipelineLayout
import re.lilith.vulkan.api.pipeline.PipelineLayoutConfig
import re.lilith.vulkan.api.pipeline.PushConstantRange
import re.lilith.vulkan.api.pipeline.ShaderModuleInfo
import re.lilith.vulkan.api.pipeline.createShaderModule
import re.lilith.vulkan.api.pipeline.ShaderStageFlags
import re.lilith.vulkan.api.pipeline.createComputePipeline
import re.lilith.vulkan.api.pipeline.ComputePipeline as VkComputePipeline

internal class VulkanComputePipeline(
    private val owner: VulkanRenderDevice,
    override val label: String,
    val description: ComputePipelineDescription,
    val pipeline: VkComputePipeline,
    val layout: PipelineLayout,
    val descriptorSetLayout: DescriptorSetLayout?,
    val bindings: List<ShaderBinding>,
    val pushConstantBytes: Int,
) : GpuComputePipeline {
    private var closed = false

    override val isClosed: Boolean get() = closed

    override fun close() {
        if (closed) return
        closed = true
        owner.scheduleRelease(pipeline)
    }

    companion object {
        fun compile(device: VulkanRenderDevice, description: ComputePipelineDescription): VulkanComputePipeline {
            val program = description.program
            val source = program.stages[ShaderStage.COMPUTE]
                ?: error("Compute program '${program.label}' has no compute stage.")
            require(program.stages.size == 1) {
                "Compute program '${program.label}' must declare only a compute stage."
            }

            val context = device.context
            val module = context.device.createShaderModule(
                ShaderModuleInfo(
                    stage = Convert.shaderStage(ShaderStage.COMPUTE),
                    entryPoint = "main",
                    spirv = VulkanShaderCompiler.compile(ShaderStage.COMPUTE, source),
                ),
            )

            val setLayout = program.bindings.takeIf(List<*>::isNotEmpty)?.let { bindings ->
                context.device.createDescriptorSetLayout(
                    DescriptorSetLayoutConfig(
                        bindings = bindings.sortedBy { it.binding }.map { binding ->
                            DescriptorSetLayoutBinding(
                                binding = binding.binding,
                                descriptorType = Convert.descriptorType(binding.kind),
                                stageFlags = ShaderStageFlags.Compute,
                            )
                        },
                    ),
                )
            }

            val layout = context.device.createPipelineLayout(
                PipelineLayoutConfig(
                    descriptorSetLayouts = listOfNotNull(setLayout),
                    pushConstantRanges = if (program.pushConstantBytes > 0) {
                        listOf(PushConstantRange(0, program.pushConstantBytes, ShaderStageFlags.Compute))
                    } else {
                        emptyList()
                    },
                ),
            )

            val pipeline = context.device.createComputePipeline(
                ComputePipelineConfig(shader = module, layout = layout, cache = context.pipelineCache),
            )
            DebugNames.set(context.device, pipeline, program.label)

            return VulkanComputePipeline(
                owner = device,
                label = program.label,
                description = description,
                pipeline = pipeline,
                layout = layout,
                descriptorSetLayout = setLayout,
                bindings = program.bindings,
                pushConstantBytes = program.pushConstantBytes,
            ).also {
                require(program.bindings.none { binding -> binding.kind == BindingKind.TEXTURE } || setLayout != null) {
                    "Compute program '${program.label}' declares textures but produced no descriptor layout."
                }
            }
        }
    }
}

@file:Suppress("DEPRECATION")

package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack

internal object LogicalDevicePipelineSupport {
    fun createPipelineCache(device: LogicalDevice, initialData: ByteArray): PipelineCache = pushStack { stack ->
        val data = if (initialData.isEmpty()) {
            null
        } else {
            org.lwjgl.system.MemoryUtil.memAlloc(initialData.size).put(0, initialData)
        }
        try {
            val createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)
                .pInitialData(data)

            val pointer = stack.mallocLong(1)
            checkVulkanResult(
                VK10.vkCreatePipelineCache(device.handle, createInfo, null, pointer),
                "Creating pipeline cache"
            )
            device.register(PipelineCache(device, pointer[0], initialData))
        } finally {
            data?.let(org.lwjgl.system.MemoryUtil::memFree)
        }
    }
}

fun LogicalDevice.createShaderModule(info: ShaderModuleInfo): ShaderModule = pushStack { stack ->
    val code = stack.malloc(info.spirv.size)
    code.put(0, info.spirv)

    val createInfo = VkShaderModuleCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
        .pCode(code)

    val pointer = stack.mallocLong(1)
    checkVulkanResult(VK10.vkCreateShaderModule(handle, createInfo, null, pointer), "Creating shader module")
    register(ShaderModule(this, pointer[0], info))
}

fun LogicalDevice.createPipelineLayout(
    config: PipelineLayoutConfig = PipelineLayoutConfig(),
): PipelineLayout = pushStack { stack ->
    require(config.descriptorSetLayouts.all { it.device === this }) { "All descriptor set layouts must belong to this logical device." }

    val setLayouts = if (config.descriptorSetLayouts.isEmpty()) {
        null
    } else {
        stack.mallocLong(config.descriptorSetLayouts.size).also { handles ->
            config.descriptorSetLayouts.forEachIndexed { index, layout -> handles.put(index, layout.handle) }
        }
    }

    val pushConstants = if (config.pushConstantRanges.isEmpty()) {
        null
    } else {
        VkPushConstantRange.calloc(config.pushConstantRanges.size, stack).also { ranges ->
            config.pushConstantRanges.forEachIndexed { index, range ->
                ranges[index]
                    .offset(range.offset)
                    .size(range.size)
                    .stageFlags(range.stageFlags.vkBits)
            }
        }
    }

    val createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
        .pSetLayouts(setLayouts)
        .pPushConstantRanges(pushConstants)

    val pointer = stack.mallocLong(1)
    checkVulkanResult(VK10.vkCreatePipelineLayout(handle, createInfo, null, pointer), "Creating pipeline layout")
    register(PipelineLayout(this, pointer[0], config))
}

fun LogicalDevice.createGraphicsPipeline(config: GraphicsPipelineConfig): GraphicsPipeline = pushStack { stack ->
    require(config.shaders.isNotEmpty()) { "At least one shader stage is required." }
    require(config.shaders.all { it.device === this }) { "All shader modules must belong to this logical device." }
    require(config.layout.device === this) { "Pipeline layout must belong to this logical device." }
    require(config.dynamicStates.distinct().size == config.dynamicStates.size) {
        "Dynamic states must not contain duplicates."
    }
    require(DynamicState.Viewport in config.dynamicStates) {
        "Graphics pipelines currently require DynamicState.Viewport."
    }
    require(DynamicState.Scissor in config.dynamicStates) {
        "Graphics pipelines currently require DynamicState.Scissor."
    }

    val shaderStages = VkPipelineShaderStageCreateInfo.calloc(config.shaders.size, stack)
    config.shaders.forEachIndexed { index, shader ->
        val specialization = config.shaderSpecializations[shader]
        shaderStages[index]
            .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(shader.info.stage.vkValue)
            .module(shader.handle)
            .pName(stack.UTF8(shader.info.entryPoint))
        if (specialization != null) {
            shaderStages[index].pSpecializationInfo(stack.toVkSpecializationInfo(specialization))
        }
    }

    val vertexBindings = if (config.vertexInput.bindings.isEmpty()) {
        null
    } else {
        VkVertexInputBindingDescription.calloc(config.vertexInput.bindings.size, stack).also { bindings ->
            config.vertexInput.bindings.forEachIndexed { index, binding ->
                bindings[index]
                    .binding(binding.binding)
                    .stride(binding.stride)
                    .inputRate(binding.inputRate.vkValue)
            }
        }
    }
    val vertexAttributes = if (config.vertexInput.attributes.isEmpty()) {
        null
    } else {
        VkVertexInputAttributeDescription.calloc(config.vertexInput.attributes.size, stack).also { attributes ->
            config.vertexInput.attributes.forEachIndexed { index, attribute ->
                attributes[index]
                    .location(attribute.location)
                    .binding(attribute.binding)
                    .format(attribute.format.vkValue)
                    .offset(attribute.offset)
            }
        }
    }
    val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
        .pVertexBindingDescriptions(vertexBindings)
        .pVertexAttributeDescriptions(vertexAttributes)

    val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
        .topology(config.topology.vkValue)
        .primitiveRestartEnable(config.primitiveRestartEnable)

    val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
        .viewportCount(config.viewportState.viewportCount)
        .scissorCount(config.viewportState.scissorCount)

    val rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
        .depthClampEnable(config.rasterization.depthClampEnable)
        .rasterizerDiscardEnable(config.rasterization.rasterizerDiscardEnable)
        .polygonMode(config.rasterization.polygonMode.vkValue)
        .cullMode(config.rasterization.cullMode.vkValue)
        .frontFace(config.rasterization.frontFace.vkValue)
        .depthBiasEnable(config.rasterization.depthBiasEnable)
        .depthBiasConstantFactor(config.rasterization.depthBiasConstantFactor)
        .depthBiasClamp(config.rasterization.depthBiasClamp)
        .depthBiasSlopeFactor(config.rasterization.depthBiasSlopeFactor)
        .lineWidth(config.rasterization.lineWidth)

    val multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
        .rasterizationSamples(config.multisampling.samples.vkValue)
        .sampleShadingEnable(config.multisampling.sampleShadingEnable)
        .minSampleShading(config.multisampling.minSampleShading)
        .alphaToCoverageEnable(config.multisampling.alphaToCoverageEnable)
        .alphaToOneEnable(config.multisampling.alphaToOneEnable)

    val depthStencil = config.depthStencil?.let { state ->
        VkPipelineDepthStencilStateCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
            .depthTestEnable(state.depthTestEnable)
            .depthWriteEnable(state.depthWriteEnable)
            .depthCompareOp(state.depthCompareOperation.vkValue)
            .depthBoundsTestEnable(state.depthBoundsTestEnable)
            .stencilTestEnable(state.stencilTestEnable)
            .minDepthBounds(state.minDepthBounds)
            .maxDepthBounds(state.maxDepthBounds)
            .also { createInfo ->
                createInfo.front().populate(state.front)
                createInfo.back().populate(state.back)
            }
    }

    val rendering = when (val rendering = config.rendering) {
        is DynamicRenderingPipelineState -> {
            val colorFormats = if (rendering.colorFormats.isEmpty()) {
                null
            } else {
                stack.mallocInt(rendering.colorFormats.size).also { formats ->
                    rendering.colorFormats.forEachIndexed { index, format -> formats.put(index, format.vkValue) }
                }
            }
            PipelineRenderingInfo(
                colorAttachmentCount = rendering.colorFormats.size,
                renderPassHandle = VK10.VK_NULL_HANDLE,
                subpassIndex = 0,
                renderingAddress = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType(VK13.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
                    .colorAttachmentCount(rendering.colorFormats.size)
                    .pColorAttachmentFormats(colorFormats)
                    .depthAttachmentFormat(rendering.depthFormat?.vkValue ?: VK10.VK_FORMAT_UNDEFINED)
                    .stencilAttachmentFormat(rendering.stencilFormat?.vkValue ?: VK10.VK_FORMAT_UNDEFINED)
                    .address(),
            )
        }

        is RenderPassPipelineState -> {
            require(rendering.renderPass.device === this) { "Render pass must belong to this logical device." }
            val subpass = rendering.renderPass.layout.subpasses.getOrNull(rendering.subpass)
                ?: error("Render pass does not define subpass ${rendering.subpass}.")
            PipelineRenderingInfo(
                colorAttachmentCount = subpass.colorAttachments.size,
                renderPassHandle = rendering.renderPass.handle,
                subpassIndex = rendering.subpass,
                renderingAddress = 0L,
            )
        }
    }

    require(config.colorBlend.attachments.isEmpty() || config.colorBlend.attachments.size == rendering.colorAttachmentCount) {
        "Color-blend attachment count must match the number of color attachments exposed by the pipeline rendering configuration."
    }
    require(config.shaderSpecializations.keys.all { it in config.shaders }) {
        "Graphics pipeline shader specializations must reference shader modules present in the pipeline shader list."
    }

    val blendAttachmentStates = when {
        rendering.colorAttachmentCount == 0 -> emptyList()
        config.colorBlend.attachments.isEmpty() -> List(rendering.colorAttachmentCount) { ColorBlendAttachmentState() }
        else -> config.colorBlend.attachments
    }
    val blendAttachments = if (blendAttachmentStates.isEmpty()) {
        null
    } else {
        VkPipelineColorBlendAttachmentState.calloc(blendAttachmentStates.size, stack).also { attachments ->
            blendAttachmentStates.forEachIndexed { index, attachment ->
                attachments[index]
                    .blendEnable(attachment.blendEnable)
                    .srcColorBlendFactor(attachment.sourceColorBlendFactor.vkValue)
                    .dstColorBlendFactor(attachment.destinationColorBlendFactor.vkValue)
                    .colorBlendOp(attachment.colorBlendOperation.vkValue)
                    .srcAlphaBlendFactor(attachment.sourceAlphaBlendFactor.vkValue)
                    .dstAlphaBlendFactor(attachment.destinationAlphaBlendFactor.vkValue)
                    .alphaBlendOp(attachment.alphaBlendOperation.vkValue)
                    .colorWriteMask(attachment.colorWriteMask.vkBits)
            }
        }
    }

    val colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        .logicOpEnable(config.colorBlend.logicOperationEnable)
        .logicOp(config.colorBlend.logicOperation.vkValue)
        .pAttachments(blendAttachments)
    config.colorBlend.blendConstants.forEachIndexed { index, value -> colorBlend.blendConstants(index, value) }

    val dynamicStates = if (config.dynamicStates.isEmpty()) {
        null
    } else {
        stack.ints(*config.dynamicStates.map(DynamicState::vkValue).toIntArray())
    }
    val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
        .pDynamicStates(dynamicStates)

    val createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
    createInfo[0]
        .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
        .pNext(rendering.renderingAddress)
        .pStages(shaderStages)
        .pVertexInputState(vertexInput)
        .pInputAssemblyState(inputAssembly)
        .pViewportState(viewportState)
        .pRasterizationState(rasterization)
        .pMultisampleState(multisample)
        .pDepthStencilState(depthStencil)
        .pColorBlendState(colorBlend)
        .pDynamicState(dynamicState)
        .layout(config.layout.handle)
        .renderPass(rendering.renderPassHandle)
        .subpass(rendering.subpassIndex)

    val pointer = stack.mallocLong(1)
    checkVulkanResult(
        VK10.vkCreateGraphicsPipelines(
            handle,
            config.cache?.handle ?: VK10.VK_NULL_HANDLE,
            createInfo,
            null,
            pointer
        ), "Creating graphics pipeline"
    )
    register(GraphicsPipeline(this, pointer[0], config))
}

fun LogicalDevice.createComputePipeline(config: ComputePipelineConfig): ComputePipeline = pushStack { stack ->
    require(config.shader.device === this) { "Compute shader module must belong to this logical device." }
    require(config.layout.device === this) { "Pipeline layout must belong to this logical device." }
    require(config.shader.info.stage == ShaderStage.Compute) { "Compute pipelines require a shader module with ShaderStage.Compute." }

    val shaderStage = VkPipelineShaderStageCreateInfo.calloc(1, stack)
    shaderStage[0]
        .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
        .stage(config.shader.info.stage.vkValue)
        .module(config.shader.handle)
        .pName(stack.UTF8(config.shader.info.entryPoint))
    if (config.specialization != null) {
        shaderStage[0].pSpecializationInfo(stack.toVkSpecializationInfo(config.specialization))
    }

    val createInfo = VkComputePipelineCreateInfo.calloc(1, stack)
    createInfo[0]
        .sType(VK10.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
        .stage(shaderStage[0])
        .layout(config.layout.handle)

    val pointer = stack.mallocLong(1)
    checkVulkanResult(
        VK10.vkCreateComputePipelines(
            handle,
            config.cache?.handle ?: VK10.VK_NULL_HANDLE,
            createInfo,
            null,
            pointer
        ), "Creating compute pipeline"
    )
    register(ComputePipeline(this, pointer[0], config))
}

private data class PipelineRenderingInfo(
    val colorAttachmentCount: Int,
    val renderPassHandle: Long,
    val subpassIndex: Int,
    val renderingAddress: Long,
)

private fun VkStencilOpState.populate(state: StencilOperationState): VkStencilOpState =
    failOp(state.failOperation.vkValue)
        .passOp(state.passOperation.vkValue)
        .depthFailOp(state.depthFailOperation.vkValue)
        .compareOp(state.compareOperation.vkValue)
        .compareMask(state.compareMask)
        .writeMask(state.writeMask)
        .reference(state.reference)

private fun org.lwjgl.system.MemoryStack.toVkSpecializationInfo(specialization: SpecializationInfo): VkSpecializationInfo {
    val mapEntries = VkSpecializationMapEntry.calloc(specialization.mapEntries.size, this)
    specialization.mapEntries.forEachIndexed { index, entry ->
        mapEntries[index]
            .constantID(entry.constantId)
            .offset(entry.offset)
            .size(entry.size.toLong())
    }
    val data = malloc(specialization.data.size)
    data.put(0, specialization.data)
    return VkSpecializationInfo.calloc(this)
        .pMapEntries(mapEntries)
        .pData(data)
}


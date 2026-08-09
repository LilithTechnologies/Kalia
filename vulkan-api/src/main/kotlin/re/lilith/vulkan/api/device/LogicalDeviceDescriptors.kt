package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.descriptor.*
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack

internal object LogicalDeviceDescriptors {
    fun createDescriptorSetLayout(
        device: LogicalDevice,
        bindings: List<DescriptorSetLayoutBinding>,
    ): DescriptorSetLayout = createDescriptorSetLayout(device, DescriptorSetLayoutConfig(bindings = bindings))

    fun createDescriptorSetLayout(
        device: LogicalDevice,
        config: DescriptorSetLayoutConfig,
    ): DescriptorSetLayout = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        require(config.bindings.distinctBy(DescriptorSetLayoutBinding::binding).size == config.bindings.size) {
            "Descriptor set layout bindings must have unique binding indices."
        }
        if (config.isPushDescriptor) {
            require(PUSH_DESCRIPTOR_EXTENSION_NAME in device.enabledExtensions) {
                "Push-descriptor layouts require enabling $PUSH_DESCRIPTOR_EXTENSION_NAME on the logical device."
            }
        }
        val highestBinding = config.bindings.maxOfOrNull(DescriptorSetLayoutBinding::binding)
        config.bindings.forEach { binding ->
            if (binding.bindingFlags.contains(re.lilith.vulkan.api.descriptor.DescriptorBindingFlags.PartiallyBound)) {
                require(device.config.features.descriptorBindingPartiallyBound) {
                    "DescriptorBindingFlags.PartiallyBound requires enabling descriptorBindingPartiallyBound on the logical device."
                }
            }
            if (binding.bindingFlags.contains(re.lilith.vulkan.api.descriptor.DescriptorBindingFlags.UpdateUnusedWhilePending)) {
                require(device.config.features.descriptorBindingUpdateUnusedWhilePending) {
                    "DescriptorBindingFlags.UpdateUnusedWhilePending requires enabling descriptorBindingUpdateUnusedWhilePending on the logical device."
                }
            }
            if (binding.bindingFlags.contains(re.lilith.vulkan.api.descriptor.DescriptorBindingFlags.VariableDescriptorCount)) {
                require(device.config.features.descriptorBindingVariableDescriptorCount) {
                    "DescriptorBindingFlags.VariableDescriptorCount requires enabling descriptorBindingVariableDescriptorCount on the logical device."
                }
                require(binding.binding == highestBinding) {
                    "Variable-descriptor-count bindings must use the highest binding number in the descriptor set layout."
                }
            }
        }

        val vkBindings = VkDescriptorSetLayoutBinding.calloc(config.bindings.size, stack)
        config.bindings.forEachIndexed { index, binding ->
            vkBindings[index]
                .binding(binding.binding)
                .descriptorType(binding.descriptorType.vkValue)
                .descriptorCount(binding.descriptorCount)
                .stageFlags(binding.stageFlags.vkBits)
        }

        val bindingFlags =
            if (config.bindings.any { it.bindingFlags != re.lilith.vulkan.api.descriptor.DescriptorBindingFlags.None }) {
                stack.ints(*config.bindings.map { it.bindingFlags.vkBits }.toIntArray())
            } else {
                null
            }
        val bindingFlagsInfo = bindingFlags?.let { flags ->
            VkDescriptorSetLayoutBindingFlagsCreateInfoEXT.calloc(stack)
                .sType(EXTDescriptorIndexing.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT)
                .pBindingFlags(flags)
        }

        val createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .flags(
                (if (config.isPushDescriptor) KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR else 0) or
                        (if (config.allowUpdateAfterBindPool) EXTDescriptorIndexing.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT else 0),
            )
            .pBindings(vkBindings)
        if (bindingFlagsInfo != null) {
            createInfo.pNext(bindingFlagsInfo.address())
        }

        val pointer = stack.mallocLong(1)
        checkVulkanResult(
            VK10.vkCreateDescriptorSetLayout(device.handle, createInfo, null, pointer),
            "Creating descriptor set layout"
        )
        registrar.register(DescriptorSetLayout(device, pointer[0], config))
    }

    fun createDescriptorPool(device: LogicalDevice, config: DescriptorPoolConfig): DescriptorPool = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val poolSizes = VkDescriptorPoolSize.calloc(config.poolSizes.size, stack)
        config.poolSizes.forEachIndexed { index, size ->
            poolSizes[index]
                .type(size.descriptorType.vkValue)
                .descriptorCount(size.descriptorCount)
        }

        val createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .flags(
                (if (config.allowIndividualFree) VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT else 0) or
                        (if (config.allowUpdateAfterBind) EXTDescriptorIndexing.VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT else 0),
            )
            .maxSets(config.maxSets)
            .pPoolSizes(poolSizes)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(
            VK10.vkCreateDescriptorPool(device.handle, createInfo, null, pointer),
            "Creating descriptor pool"
        )
        registrar.register(DescriptorPool(device, pointer[0], config))
    }

    fun allocateDescriptorSets(
        device: LogicalDevice,
        pool: DescriptorPool,
        allocations: List<DescriptorSetAllocation>,
    ): List<DescriptorSet> = pushStack { stack ->
        require(allocations.isNotEmpty()) { "At least one descriptor set layout is required." }
        require(pool.device === device) { "Descriptor pool must belong to this logical device." }

        val layouts = allocations.map(DescriptorSetAllocation::layout)
        require(layouts.all { it.device === device }) { "Descriptor set layouts must belong to this logical device." }
        require(layouts.none(DescriptorSetLayout::isPushDescriptor)) {
            "Push-descriptor set layouts cannot be used to allocate descriptor sets from a descriptor pool."
        }
        require(!pool.config.allowUpdateAfterBind || layouts.all { it.config.allowUpdateAfterBindPool }) {
            "Descriptor pools created for update-after-bind must only allocate compatible descriptor set layouts."
        }

        val layoutHandles = stack.mallocLong(layouts.size)
        layouts.forEachIndexed { index, layout -> layoutHandles.put(index, layout.handle) }

        val variableDescriptorCounts = allocations.mapIndexed { index, allocation ->
            val variableBinding = allocation.layout.bindings.lastOrNull { binding ->
                binding.bindingFlags.contains(re.lilith.vulkan.api.descriptor.DescriptorBindingFlags.VariableDescriptorCount)
            }
            when {
                variableBinding == null -> {
                    require(allocation.variableDescriptorCount == null) {
                        "Descriptor set allocation $index specified a variable descriptor count for a layout without a variable-count binding."
                    }
                    0
                }

                else -> {
                    val count = allocation.variableDescriptorCount ?: variableBinding.descriptorCount
                    require(count in 0..variableBinding.descriptorCount) {
                        "Variable descriptor count must be in the range [0, ${variableBinding.descriptorCount}] for binding ${variableBinding.binding}."
                    }
                    count
                }
            }
        }

        val allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(pool.handle)
            .pSetLayouts(layoutHandles)
        if (variableDescriptorCounts.any { it != 0 }) {
            allocateInfo.pNext(
                VkDescriptorSetVariableDescriptorCountAllocateInfo.calloc(stack)
                    .sType(EXTDescriptorIndexing.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO_EXT)
                    .pDescriptorCounts(stack.ints(*variableDescriptorCounts.toIntArray()))
                    .address(),
            )
        }

        val setHandles = stack.mallocLong(layouts.size)
        checkVulkanResult(
            VK10.vkAllocateDescriptorSets(device.handle, allocateInfo, setHandles),
            "Allocating descriptor sets"
        )
        List(layouts.size) { index -> DescriptorSet(pool, layouts[index], setHandles[index]) }
    }

    fun createDescriptorUpdateTemplate(
        device: LogicalDevice,
        config: DescriptorUpdateTemplateConfig,
    ): DescriptorUpdateTemplate {
        require(config.descriptorSetLayout.device === device) {
            "Descriptor update templates must target descriptor set layouts owned by this logical device."
        }

        val layoutBindingsByIndex = config.descriptorSetLayout.bindings.associateBy(DescriptorSetLayoutBinding::binding)
        val entryLayouts = mutableListOf<DescriptorUpdateTemplateEntryLayout>()
        config.entries.forEach { entry ->
            val binding = layoutBindingsByIndex[entry.binding]
                ?: error("Descriptor update template entry references unknown binding ${entry.binding}.")
            require(entry.arrayElement + entry.descriptorCount <= binding.descriptorCount) {
                "Descriptor update template entry for binding ${entry.binding} exceeds the descriptor count declared by the layout."
            }
            require(entry.descriptorType == binding.descriptorType) {
                "Descriptor update template entry for binding ${entry.binding} must use descriptor type ${binding.descriptorType}."
            }

            val kind = when (entry) {
                is DescriptorUpdateTemplateEntry.BufferEntry -> DescriptorTemplateEntryKind.Buffer
                is DescriptorUpdateTemplateEntry.ImageEntry -> DescriptorTemplateEntryKind.Image
            }
            entryLayouts += DescriptorUpdateTemplateEntryLayout(
                binding = entry.binding,
                arrayElement = entry.arrayElement,
                descriptorType = entry.descriptorType,
                descriptorCount = entry.descriptorCount,
                kind = kind,
            )
        }

        return DescriptorUpdateTemplate(
            device = device,
            config = config,
            entryLayouts = entryLayouts,
        )
    }

    fun createSampler(device: LogicalDevice, config: SamplerConfig): Sampler = pushStack { stack ->
        val registrar: ResourceRegistrar = device
        val createInfo = VkSamplerCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(config.magFilter.vkValue)
            .minFilter(config.minFilter.vkValue)
            .mipmapMode(config.mipmapMode.vkValue)
            .addressModeU(config.addressModeU.vkValue)
            .addressModeV(config.addressModeV.vkValue)
            .addressModeW(config.addressModeW.vkValue)
            .mipLodBias(config.mipLodBias)
            .anisotropyEnable(config.anisotropyEnable)
            .maxAnisotropy(config.maxAnisotropy)
            .compareEnable(false)
            .minLod(config.minLod)
            .maxLod(config.maxLod)
            .unnormalizedCoordinates(false)

        val pointer = stack.mallocLong(1)
        checkVulkanResult(VK10.vkCreateSampler(device.handle, createInfo, null, pointer), "Creating sampler")
        registrar.register(Sampler(device, pointer[0], config))
    }

    fun updateDescriptorSets(device: LogicalDevice, writes: List<DescriptorSetWrite>) = pushStack { stack ->
        if (writes.isEmpty()) {
            return@pushStack
        }

        VK10.vkUpdateDescriptorSets(device.handle, encodeDescriptorWrites(stack, device, writes), null)
    }
}


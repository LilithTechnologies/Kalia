package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.device.LogicalDevice

class DescriptorUpdateTemplate internal constructor(
    internal val device: LogicalDevice,
    val config: DescriptorUpdateTemplateConfig,
    private val entryLayouts: List<DescriptorUpdateTemplateEntryLayout>,
) : AutoCloseable {
    fun update(descriptorSet: DescriptorSet, writes: List<DescriptorTemplateWrite>) {
        require(descriptorSet.device === device) { "Descriptor set must belong to the same logical device as the update template." }
        require(descriptorSet.layout === config.descriptorSetLayout) {
            "Descriptor set layout must match the descriptor update template layout."
        }
        require(writes.size == entryLayouts.size) {
            "Descriptor template updates must provide exactly ${entryLayouts.size} write(s)."
        }

        device.updateDescriptorSets(
            writes.mapIndexed { index, write ->
                val layout = entryLayouts[index]
                require(write.binding == layout.binding) { "Descriptor template write order must match the template entry binding order." }
                require(write.arrayElement == layout.arrayElement) { "Descriptor template write arrayElement must match the template entry." }
                require(write.descriptorType == layout.descriptorType) { "Descriptor template write descriptorType must match the template entry." }

                when (layout.kind) {
                    DescriptorTemplateEntryKind.Buffer -> {
                        require(write is DescriptorTemplateWrite.BufferWrite) { "Descriptor template entry $index expects buffer descriptors." }
                        require(write.descriptors.size == layout.descriptorCount) {
                            "Descriptor template entry $index expects ${layout.descriptorCount} buffer descriptor(s)."
                        }
                        DescriptorSetWrite.BufferWrite(
                            targetSet = descriptorSet,
                            binding = write.binding,
                            descriptorType = write.descriptorType,
                            descriptors = write.descriptors,
                            arrayElement = write.arrayElement,
                        )
                    }

                    DescriptorTemplateEntryKind.Image -> {
                        require(write is DescriptorTemplateWrite.ImageWrite) { "Descriptor template entry $index expects image descriptors." }
                        require(write.descriptors.size == layout.descriptorCount) {
                            "Descriptor template entry $index expects ${layout.descriptorCount} image descriptor(s)."
                        }
                        DescriptorSetWrite.ImageWrite(
                            targetSet = descriptorSet,
                            binding = write.binding,
                            descriptorType = write.descriptorType,
                            descriptors = write.descriptors,
                            arrayElement = write.arrayElement,
                        )
                    }
                }
            },
        )
    }

    override fun close() {
        // No-op: this Kotlin-first template wraps reusable descriptor-write metadata.
    }
}

internal data class DescriptorUpdateTemplateEntryLayout(
    val binding: Int,
    val arrayElement: Int,
    val descriptorType: DescriptorType,
    val descriptorCount: Int,
    val kind: DescriptorTemplateEntryKind,
)

internal enum class DescriptorTemplateEntryKind {
    Buffer,
    Image,
}

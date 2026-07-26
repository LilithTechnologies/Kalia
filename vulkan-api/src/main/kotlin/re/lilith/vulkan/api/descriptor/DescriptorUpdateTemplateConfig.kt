package re.lilith.vulkan.api.descriptor

data class DescriptorUpdateTemplateConfig(
    val descriptorSetLayout: DescriptorSetLayout,
    val entries: List<DescriptorUpdateTemplateEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "At least one descriptor update template entry is required." }
    }
}


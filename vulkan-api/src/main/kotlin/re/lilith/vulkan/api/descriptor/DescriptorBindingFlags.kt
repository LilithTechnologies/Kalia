package re.lilith.vulkan.api.descriptor

import org.lwjgl.vulkan.EXTDescriptorIndexing

@JvmInline
value class DescriptorBindingFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: DescriptorBindingFlags): DescriptorBindingFlags =
        DescriptorBindingFlags(vkBits or other.vkBits)

    operator fun contains(other: DescriptorBindingFlags): Boolean = vkBits and other.vkBits == other.vkBits

    companion object {
        val None = DescriptorBindingFlags(0)
        val UpdateUnusedWhilePending =
            DescriptorBindingFlags(EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT)
        val PartiallyBound = DescriptorBindingFlags(EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT)
        val VariableDescriptorCount =
            DescriptorBindingFlags(EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT_EXT)
    }
}


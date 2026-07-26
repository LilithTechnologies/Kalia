package re.lilith.vulkan.api.types.enum

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class SampleCount internal constructor(internal val vkValue: Int) {
    companion object {
        val One = SampleCount(VulkanConstants.SampleCounts.x1)
        val Two = SampleCount(VulkanConstants.SampleCounts.x2)
        val Four = SampleCount(VulkanConstants.SampleCounts.x4)
        val Eight = SampleCount(VulkanConstants.SampleCounts.x8)
        val Sixteen = SampleCount(VulkanConstants.SampleCounts.x16)
        val ThirtyTwo = SampleCount(VulkanConstants.SampleCounts.x32)
        val SixtyFour = SampleCount(VulkanConstants.SampleCounts.x64)
    }
}

enum class AttachmentLoadOperation(internal val vkValue: Int) {
    Load(VulkanConstants.AttachmentLoadOps.load),
    Clear(VulkanConstants.AttachmentLoadOps.clear),
    DontCare(VulkanConstants.AttachmentLoadOps.dontCare),
}

enum class AttachmentStoreOperation(internal val vkValue: Int) {
    Store(VulkanConstants.AttachmentStoreOps.store),
    DontCare(VulkanConstants.AttachmentStoreOps.dontCare),
}

enum class ComponentSwizzle(internal val vkValue: Int) {
    Identity(VulkanConstants.ComponentSwizzles.identity),
    Zero(VulkanConstants.ComponentSwizzles.zero),
    One(VulkanConstants.ComponentSwizzles.one),
    Red(VulkanConstants.ComponentSwizzles.r),
    Green(VulkanConstants.ComponentSwizzles.g),
    Blue(VulkanConstants.ComponentSwizzles.b),
    Alpha(VulkanConstants.ComponentSwizzles.a),
}

enum class IndexType(internal val vkValue: Int) {
    UnsignedShort(VulkanConstants.IndexTypes.unsignedShort),
    UnsignedInt(VulkanConstants.IndexTypes.unsignedInt),
    UnsignedByte(VulkanConstants.IndexTypes.unsignedByte),
}

enum class SubpassContents(internal val vkValue: Int) {
    Inline(VulkanConstants.SubpassContents.inline),
    SecondaryCommandBuffers(VulkanConstants.SubpassContents.secondaryCommandBuffers),
}

enum class SharingMode {
    Exclusive,
    Concurrent,
}

enum class PhysicalDeviceType {
    Other,
    IntegratedGpu,
    DiscreteGpu,
    VirtualGpu,
    Cpu,
}



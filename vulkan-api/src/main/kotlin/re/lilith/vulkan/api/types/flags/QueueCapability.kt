package re.lilith.vulkan.api.types.flags

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class QueueCapability internal constructor(internal val vkBits: Int) {
    operator fun plus(other: QueueCapability): QueueCapability = QueueCapability(vkBits or other.vkBits)
    operator fun contains(other: QueueCapability): Boolean = vkBits and other.vkBits == other.vkBits

    companion object {
        val None = QueueCapability(VulkanConstants.QueueCapabilities.none)
        val Graphics = QueueCapability(VulkanConstants.QueueCapabilities.graphics)
        val Compute = QueueCapability(VulkanConstants.QueueCapabilities.compute)
        val Transfer = QueueCapability(VulkanConstants.QueueCapabilities.transfer)
        val SparseBinding = QueueCapability(VulkanConstants.QueueCapabilities.sparseBinding)
        val Protected = QueueCapability(VulkanConstants.QueueCapabilities.protectedQueue)

        internal fun fromVk(bits: Int): QueueCapability = QueueCapability(bits)
    }
}



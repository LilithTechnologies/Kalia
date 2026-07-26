package re.lilith.vulkan.api.types.flags

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class MemoryHeapFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: MemoryHeapFlags): MemoryHeapFlags = MemoryHeapFlags(vkBits or other.vkBits)
    operator fun contains(other: MemoryHeapFlags): Boolean = vkBits and other.vkBits == other.vkBits

    companion object {
        val None = MemoryHeapFlags(VulkanConstants.MemoryHeapFlags.none)
        val DeviceLocal = MemoryHeapFlags(VulkanConstants.MemoryHeapFlags.deviceLocal)
        val MultiInstance = MemoryHeapFlags(VulkanConstants.MemoryHeapFlags.multiInstance)

        internal fun fromVk(bits: Int): MemoryHeapFlags = MemoryHeapFlags(bits)
    }
}


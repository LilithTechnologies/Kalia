package re.lilith.vulkan.api.memory

import re.lilith.vulkan.api.types.flags.MemoryPropertyFlags

data class MemoryType(
    val propertyFlags: MemoryPropertyFlags,
    val heapIndex: Int,
)

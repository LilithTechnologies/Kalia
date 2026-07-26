package re.lilith.vulkan.api.memory

import re.lilith.vulkan.api.types.flags.MemoryHeapFlags

data class MemoryHeap(
    val size: Long,
    val flags: MemoryHeapFlags,
)

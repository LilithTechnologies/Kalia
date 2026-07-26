package re.lilith.vulkan.api.memory

import re.lilith.vulkan.api.types.enum.SharingMode
import re.lilith.vulkan.api.types.flags.BufferUsage

data class BufferConfig(
    val size: Long,
    val usage: BufferUsage,
    val sharingMode: SharingMode = SharingMode.Exclusive,
    val queueFamilyIndices: List<Int> = emptyList(),
)
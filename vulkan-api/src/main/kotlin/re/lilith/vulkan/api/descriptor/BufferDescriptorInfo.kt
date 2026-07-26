package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.memory.Buffer

data class BufferDescriptorInfo(
    val buffer: Buffer,
    val offset: Long = 0L,
    val range: Long? = null,
)

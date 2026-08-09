package re.lilith.vulkan.api.types.transfer

data class BufferCopy(
    val sourceOffset: Long = 0L,
    val destinationOffset: Long = 0L,
    val size: Long,
)


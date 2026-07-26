package re.lilith.vulkan.api.memory

data class MemoryProperties(
    val heaps: List<MemoryHeap>,
    val types: List<MemoryType>,
)
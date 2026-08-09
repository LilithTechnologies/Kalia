package re.lilith.vulkan.api.memory

import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK10

/**
 * High-level intent describing where an allocation should live and how the host accesses it.
 *
 * Each variant maps onto a Vulkan Memory Allocator usage plus the allocation flags and memory
 * property requirements needed to reproduce the behaviour the engine relied on before VMA.
 */
enum class MemoryUsage(
    internal val vmaUsage: Int,
    internal val allocationFlags: Int,
    internal val requiredFlags: Int,
    internal val preferredFlags: Int,
) {
    /** Device-local memory with no host access (images, GPU-only buffers). */
    GpuOnly(
        vmaUsage = Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
        allocationFlags = 0,
        requiredFlags = 0,
        preferredFlags = 0,
    ),

    /** Persistently mapped, write-only host memory for streaming uploads (staging buffers). */
    Upload(
        vmaUsage = Vma.VMA_MEMORY_USAGE_AUTO,
        allocationFlags = Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT or
                Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT,
        requiredFlags = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        preferredFlags = 0,
    ),

    /** Persistently mapped host memory supporting random read/write access (dynamic GPU buffers). */
    HostRandom(
        vmaUsage = Vma.VMA_MEMORY_USAGE_AUTO,
        allocationFlags = Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT or
                Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT,
        requiredFlags = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        preferredFlags = VK10.VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
    );

    internal fun toCreateInfo(stack: MemoryStack): VmaAllocationCreateInfo =
        VmaAllocationCreateInfo.calloc(stack)
            .usage(vmaUsage)
            .flags(allocationFlags)
            .requiredFlags(requiredFlags)
            .preferredFlags(preferredFlags)

    /** Whether this usage requests a persistent host mapping (VMA_ALLOCATION_CREATE_MAPPED_BIT). */
    internal val expectsMapped: Boolean
        get() = allocationFlags and Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT != 0
}

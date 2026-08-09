package re.lilith.vulkan.api.pipeline

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource

class PipelineCache internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val initialData: ByteArray,
) : VulkanResource() {
    fun data(): ByteArray = pushStack { stack ->
        val size = stack.mallocPointer(1)
        checkVulkanResult(
            VK10.vkGetPipelineCacheData(device.handle, handle, size, null),
            "Querying pipeline cache data size"
        )
        val byteCount = size[0].toInt()
        if (byteCount == 0) {
            return@pushStack ByteArray(0)
        }

        val buffer = MemoryUtil.memAlloc(byteCount)
        try {
            checkVulkanResult(
                VK10.vkGetPipelineCacheData(device.handle, handle, size, buffer),
                "Reading pipeline cache data"
            )
            ByteArray(byteCount).also { buffer.get(it) }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    fun merge(caches: List<PipelineCache>) {
        if (caches.isEmpty()) {
            return
        }

        require(caches.all { it.device === device }) { "All pipeline caches must belong to the same logical device." }
        pushStack { stack ->
            val handles = stack.mallocLong(caches.size)
            caches.forEachIndexed { index, cache -> handles.put(index, cache.handle) }
            checkVulkanResult(VK10.vkMergePipelineCaches(device.handle, handle, handles), "Merging pipeline caches")
        }
    }

    override fun closeResource() {
        VK10.vkDestroyPipelineCache(device.handle, handle, null)
    }
}


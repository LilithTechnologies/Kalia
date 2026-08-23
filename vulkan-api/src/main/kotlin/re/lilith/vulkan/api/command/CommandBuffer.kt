package re.lilith.vulkan.api.command

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.rendering.NativeRenderingInfo
import re.lilith.vulkan.api.rendering.RenderingInfo
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.types.flags.CommandBufferUsage

/**
 * Command buffers are objects used to record commands which
 * can be subsequently submitted to a device queue for execution.
 */
class CommandBuffer internal constructor(
    internal val pool: CommandPool,
    val handle: VkCommandBuffer,
    val level: CommandBufferLevel,
) : VulkanResource() {
    val device: LogicalDevice
        get() = pool.device

    private val renderingInfos = HashMap<RenderingInfo, NativeRenderingInfo>()

    internal val vertexBindScratch: Long = MemoryUtil.nmemAllocChecked(VERTEX_BIND_SCRATCH_BYTES)

    internal fun nativeRenderingInfo(info: RenderingInfo, useExtension: Boolean): NativeRenderingInfo {
        renderingInfos[info]?.let { return it }
        if (renderingInfos.size >= MAX_CACHED_RENDERING_INFOS) {
            renderingInfos.values.forEach(NativeRenderingInfo::close)
            renderingInfos.clear()
        }
        return NativeRenderingInfo(info, useExtension).also { renderingInfos[info] = it }
    }

    fun begin(usage: CommandBufferUsage = CommandBufferUsage.OneTimeSubmit): CommandRecorder = pushStack { stack ->
        val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(usage.vkBits)

        checkVulkanResult(VK10.vkBeginCommandBuffer(handle, beginInfo), "Beginning command buffer recording")
        CommandRecorder(this)
    }

    fun reset(releaseResources: Boolean = false) {
        checkVulkanResult(
            VK10.vkResetCommandBuffer(
                handle,
                if (releaseResources) VK10.VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT else 0,
            ),
            "Resetting command buffer",
        )
    }

    internal fun endRecording() {
        checkVulkanResult(VK10.vkEndCommandBuffer(handle), "Ending command buffer recording")
    }

    override fun closeResource() {
        renderingInfos.values.forEach(NativeRenderingInfo::close)
        renderingInfos.clear()
        MemoryUtil.nmemFree(vertexBindScratch)
    }

    private companion object {
        const val MAX_CACHED_RENDERING_INFOS = 64
        const val VERTEX_BIND_SCRATCH_BYTES = 16L
    }
}

package re.lilith.vulkan.api.sync

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK12
import org.lwjgl.vulkan.VkSemaphoreSignalInfo
import org.lwjgl.vulkan.VkSemaphoreWaitInfo
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult

class TimelineSemaphore internal constructor(
    device: LogicalDevice,
    handle: Long,
) : Semaphore(device, handle) {
    fun value(): Long = MemoryStack.stackPush().use { stack ->
        val pointer = stack.mallocLong(1)
        checkVulkanResult(
            VK12.vkGetSemaphoreCounterValue(device.handle, handle, pointer),
            "Querying timeline semaphore value"
        )
        pointer[0]
    }

    fun signal(value: Long) {
        MemoryStack.stackPush().use { stack ->
            val info = VkSemaphoreSignalInfo.calloc(stack)
                .sType(VK12.VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO)
                .semaphore(handle)
                .value(value)
            checkVulkanResult(VK12.vkSignalSemaphore(device.handle, info), "Signaling timeline semaphore")
        }
    }

    fun waitFor(value: Long, timeoutNanos: Long = Long.MAX_VALUE): Boolean = MemoryStack.stackPush().use { stack ->
        val info = VkSemaphoreWaitInfo.calloc(stack)
            .sType(VK12.VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO)
            .semaphoreCount(1)
            .pSemaphores(stack.longs(handle))
            .pValues(stack.longs(value))

        VK12.vkWaitSemaphores(device.handle, info, timeoutNanos) == VK10.VK_SUCCESS
    }

    override fun closeResource() {
        VK10.vkDestroySemaphore(device.handle, handle, null)
    }
}

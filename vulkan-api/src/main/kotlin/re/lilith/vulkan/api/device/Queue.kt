package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_NULL_HANDLE
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.sync.Fence

/**
 * A single queue on a logical device.
 */
class Queue internal constructor(
    internal val device: LogicalDevice,
    val handle: VkQueue,
    val familyIndex: Int,
    val queueIndex: Int,
)

fun Queue.submit(
    submissions: List<QueueSubmission>,
    fence: Fence? = null,
) {
    require(submissions.isNotEmpty()) { "At least one submission is required." }

    pushStack { stack ->
        val submitInfos = VkSubmitInfo.calloc(submissions.size, stack)
        val timelineInfos = arrayOfNulls<VkTimelineSemaphoreSubmitInfo>(submissions.size)

        submissions.forEachIndexed { index, submission ->
            require(submission.commandBuffers.isNotEmpty()) { "Each submission must include at least one command buffer." }

            val commandBufferPointers = stack.mallocPointer(submission.commandBuffers.size)
            submission.commandBuffers.forEachIndexed { commandIndex, commandBuffer ->
                require(commandBuffer.device === device) { "Command buffers must belong to the same logical device as the queue." }
                commandBufferPointers.put(commandIndex, commandBuffer.handle.address())
            }

            val submitInfo = submitInfos[index]
                .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(commandBufferPointers)

            if (submission.waitSemaphores.isNotEmpty()) {
                val semaphores = stack.mallocLong(submission.waitSemaphores.size)
                val stages = stack.mallocInt(submission.waitSemaphores.size)
                val values = stack.mallocLong(submission.waitSemaphores.size)

                submission.waitSemaphores.forEachIndexed { waitIndex, wait ->
                    require(wait.semaphore.device === device) { "Wait semaphores must belong to the same logical device as the queue." }
                    semaphores.put(waitIndex, wait.semaphore.handle)
                    stages.put(waitIndex, wait.stageMask.vkBits)
                    values.put(waitIndex, wait.value ?: 0L)
                }

                submitInfo
                    .waitSemaphoreCount(submission.waitSemaphores.size)
                    .pWaitSemaphores(semaphores)
                    .pWaitDstStageMask(stages)

                if (submission.waitSemaphores.any { it.value != null }) {
                    val timelineInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                        .sType(VK12.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO)
                        .pWaitSemaphoreValues(values)
                    submitInfo.pNext(timelineInfo.address())
                    timelineInfos[index] = timelineInfo
                }
            }

            if (submission.signalSemaphores.isNotEmpty()) {
                val semaphores = stack.mallocLong(submission.signalSemaphores.size)
                val values = stack.mallocLong(submission.signalSemaphores.size)

                submission.signalSemaphores.forEachIndexed { signalIndex, signal ->
                    require(signal.semaphore.device === device) { "Signal semaphores must belong to the same logical device as the queue." }
                    semaphores.put(signalIndex, signal.semaphore.handle)
                    values.put(signalIndex, signal.value ?: 0L)
                }

                submitInfo.pSignalSemaphores(semaphores)

                if (submission.signalSemaphores.any { it.value != null }) {
                    val timelineInfo = timelineInfos[index] ?: VkTimelineSemaphoreSubmitInfo.calloc(stack)
                        .sType(VK12.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO)
                    timelineInfo.pSignalSemaphoreValues(values)
                    submitInfo.pNext(timelineInfo.address())
                    timelineInfos[index] = timelineInfo
                }
            }
        }

        checkVulkanResult(
            VK10.vkQueueSubmit(handle, submitInfos, fence?.handle ?: VK_NULL_HANDLE),
            "Submitting to queue family $familyIndex",
        )
    }
}

fun Queue.waitIdle() {
    checkVulkanResult(VK10.vkQueueWaitIdle(handle), "Waiting for queue idle")
}

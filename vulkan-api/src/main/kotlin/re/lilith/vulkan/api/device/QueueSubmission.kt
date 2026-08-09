package re.lilith.vulkan.api.device

import re.lilith.vulkan.api.command.CommandBuffer
import re.lilith.vulkan.api.sync.SemaphoreSignal
import re.lilith.vulkan.api.sync.SemaphoreWait

/**
 * One queue submission batch.
 */
data class QueueSubmission(
    val commandBuffers: List<CommandBuffer>,
    val waitSemaphores: List<SemaphoreWait> = emptyList(),
    val signalSemaphores: List<SemaphoreSignal> = emptyList(),
)

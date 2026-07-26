package re.lilith.vulkan.api.sync

import re.lilith.vulkan.api.types.flags.PipelineStageMask

data class SemaphoreWait(
    val semaphore: Semaphore,
    val stageMask: PipelineStageMask = PipelineStageMask.AllCommands,
    val value: Long? = null,
)

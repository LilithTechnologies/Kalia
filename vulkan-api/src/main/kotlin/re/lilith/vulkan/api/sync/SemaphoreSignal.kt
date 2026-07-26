package re.lilith.vulkan.api.sync

data class SemaphoreSignal(
    val semaphore: Semaphore,
    val value: Long? = null,
)


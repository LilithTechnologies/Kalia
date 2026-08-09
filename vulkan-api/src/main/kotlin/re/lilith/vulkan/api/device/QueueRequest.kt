package re.lilith.vulkan.api.device

/**
 * Requests queues from a single queue family.
 */
data class QueueRequest(
    val familyIndex: Int,
    val priorities: List<Float>,
)

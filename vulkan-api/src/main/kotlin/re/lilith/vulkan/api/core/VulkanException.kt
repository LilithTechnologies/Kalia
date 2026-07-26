package re.lilith.vulkan.api.core

open class VulkanException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
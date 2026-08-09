package re.lilith.vulkan.api.core

class VulkanResultException(
    val resultCode: Int,
    action: String,
) : VulkanException("$action failed with Vulkan result $resultCode")
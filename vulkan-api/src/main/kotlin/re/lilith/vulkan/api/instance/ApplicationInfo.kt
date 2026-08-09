package re.lilith.vulkan.api.instance

import re.lilith.vulkan.api.core.Version

/**
 * Describes the application and engine that will own the Vulkan instance.
 */
data class ApplicationInfo(
    val applicationName: String = "Application",
    val applicationVersion: Version = Version(0, 1, 0),
    val engineName: String = "vulkan-api",
    val engineVersion: Version = Version(0, 1, 0),
    val apiVersion: Version = Version.V1_3,
)
package re.lilith.vulkan.api.instance

/**
 * Instance-wide validation settings.
 */
data class ValidationConfig(
    val enableValidationLayer: Boolean = false,
    val enableDebugUtils: Boolean = false,
)
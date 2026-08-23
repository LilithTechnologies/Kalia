package re.lilith.vulkan.api.instance

import re.lilith.vulkan.api.core.Version

data class InstanceConfig(
    val applicationInfo: ApplicationInfo = ApplicationInfo(),
    val enabledLayers: Set<String> = emptySet(),
    val enabledExtensions: Set<String> = emptySet(),
    val validation: ValidationConfig = ValidationConfig(),
)

class InstanceConfigBuilder {
    var applicationName: String = "Application"
    var applicationVersion: Version = Version(0, 1, 0)
    var engineName: String = "vulkan-api"
    var engineVersion: Version = Version(0, 1, 0)
    var apiVersion: Version = Version.V1_3
    var enableValidationLayer: Boolean = false
    var enableDebugUtils: Boolean = false

    private val enabledLayers = linkedSetOf<String>()
    private val enabledExtensions = linkedSetOf<String>()

    fun enableLayer(name: String) {
        enabledLayers += name
    }

    fun enableExtension(name: String) {
        enabledExtensions += name
    }

    fun enableValidation() {
        enableValidationLayer = true
        enabledLayers += VALIDATION_LAYER_NAME
    }

    fun enableDebugUtils() {
        enableDebugUtils = true
    }

    fun build(): InstanceConfig = InstanceConfig(
        applicationInfo = ApplicationInfo(
            applicationName = applicationName,
            applicationVersion = applicationVersion,
            engineName = engineName,
            engineVersion = engineVersion,
            apiVersion = apiVersion,
        ),
        enabledLayers = enabledLayers.toSet(),
        enabledExtensions = enabledExtensions.toSet(),
        validation = ValidationConfig(
            enableValidationLayer = enableValidationLayer,
            enableDebugUtils = enableDebugUtils,
        ),
    )

    companion object {
        const val VALIDATION_LAYER_NAME: String = "VK_LAYER_KHRONOS_validation"
        const val DEBUG_UTILS_EXTENSION_NAME: String = "VK_EXT_debug_utils"
    }
}


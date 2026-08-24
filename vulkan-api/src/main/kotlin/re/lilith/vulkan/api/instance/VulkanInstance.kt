package re.lilith.vulkan.api.instance

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.Vulkan
import re.lilith.vulkan.api.device.PhysicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.internal.vk.pointerBufferOf
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource

/**
 * Vulkan instance wrapper. This is the root owner for instance-scoped native objects.
 */
class VulkanInstance internal constructor(
    val handle: VkInstance,
    val config: InstanceConfig,
    val availableLayers: Set<String>,
    val availableExtensions: Set<String>,
    val enabledLayers: Set<String>,
) : VulkanResource() {
    fun enumeratePhysicalDevices(): List<PhysicalDevice> = pushStack { stack ->
        val count = stack.ints(0)
        checkVulkanResult(VK10.vkEnumeratePhysicalDevices(handle, count, null), "Enumerating physical devices")
        if (count[0] == 0) {
            return@pushStack emptyList()
        }

        val physicalDevices = stack.mallocPointer(count[0])
        checkVulkanResult(VK10.vkEnumeratePhysicalDevices(handle, count, physicalDevices), "Reading physical devices")

        List(count[0]) { index ->
            PhysicalDevice.query(this, org.lwjgl.vulkan.VkPhysicalDevice(physicalDevices[index], handle))
        }
    }

    fun selectPhysicalDevice(
        selector: (PhysicalDevice) -> Int = { 0 },
        predicate: (PhysicalDevice) -> Boolean
    ): PhysicalDevice =
        enumeratePhysicalDevices().sortedBy(selector).firstOrNull(predicate)
            ?: error("No physical device matched the requested predicate.")

    override fun closeResource() {
        VK10.vkDestroyInstance(handle, null)
    }

    companion object {
        private const val PORTABILITY_ENUMERATION_EXTENSION_NAME = "VK_KHR_portability_enumeration"
        private const val INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR = 0x00000001

        internal fun create(config: InstanceConfig): VulkanInstance = pushStack { stack ->
            require(config.applicationInfo.apiVersion <= Vulkan.supportedApiVersion) {
                "Requested Vulkan API ${config.applicationInfo.apiVersion}, but the runtime only supports ${Vulkan.supportedApiVersion}."
            }

            val supportedLayers = enumerateLayers()
            val supportedExtensions = enumerateExtensions()

            val enabledLayers = buildSet {
                addAll(config.enabledLayers)
                if (config.validation.enableValidationLayer &&
                    InstanceConfigBuilder.VALIDATION_LAYER_NAME in supportedLayers
                ) {
                    add(InstanceConfigBuilder.VALIDATION_LAYER_NAME)
                }
            }

            val syncValidationAvailable =
                InstanceConfigBuilder.VALIDATION_LAYER_NAME in enabledLayers &&
                        "VK_EXT_validation_features" in supportedExtensions

            val requestedExtensions = buildSet {
                addAll(config.enabledExtensions)
                if (config.validation.enableDebugUtils) {
                    add(InstanceConfigBuilder.DEBUG_UTILS_EXTENSION_NAME)
                }
                if (syncValidationAvailable) {
                    add("VK_EXT_validation_features")
                }
            }

            // SDL reports this ext on macOS
            // Older Vulkan loaders may not advertise it, even though their portability devices are usable
            val unsupportedExtensions = requestedExtensions - supportedExtensions
            require(unsupportedExtensions.all { it == PORTABILITY_ENUMERATION_EXTENSION_NAME }) {
                "Instance configuration requested unsupported extensions: $unsupportedExtensions"
            }
            val enabledExtensions = requestedExtensions - unsupportedExtensions

            require(enabledLayers.all { it in supportedLayers }) {
                "Instance configuration requested unsupported layers: ${enabledLayers - supportedLayers}"
            }
            val applicationInfo = VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(config.applicationInfo.applicationName))
                .applicationVersion(config.applicationInfo.applicationVersion.encoded)
                .pEngineName(stack.UTF8(config.applicationInfo.engineName))
                .engineVersion(config.applicationInfo.engineVersion.encoded)
                .apiVersion(config.applicationInfo.apiVersion.encoded)

            val createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .flags(
                    if (PORTABILITY_ENUMERATION_EXTENSION_NAME in enabledExtensions) {
                        INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
                    } else {
                        0
                    }
                )
                .pApplicationInfo(applicationInfo)
                .ppEnabledLayerNames(if (enabledLayers.isEmpty()) null else stack.pointerBufferOf(enabledLayers))
                .ppEnabledExtensionNames(
                    if (enabledExtensions.isEmpty()) null else stack.pointerBufferOf(
                        enabledExtensions
                    )
                )

            if (syncValidationAvailable) {
                val enabledFeatures = stack.ints(
                    EXTValidationFeatures.VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT,
                )
                val validationFeatures = VkValidationFeaturesEXT.calloc(stack)
                    .sType(EXTValidationFeatures.VK_STRUCTURE_TYPE_VALIDATION_FEATURES_EXT)
                    .pEnabledValidationFeatures(enabledFeatures)
                createInfo.pNext(validationFeatures.address())
            }

            val pointer = stack.mallocPointer(1)
            checkVulkanResult(VK10.vkCreateInstance(createInfo, null, pointer), "Creating Vulkan instance")

            val instance = VkInstance(pointer[0], createInfo)

            VulkanInstance(
                handle = instance,
                config = config,
                availableLayers = supportedLayers,
                availableExtensions = supportedExtensions,
                enabledLayers = enabledLayers,
            )
        }

        private fun enumerateLayers(): Set<String> = pushStack { stack ->
            val count = stack.ints(0)
            checkVulkanResult(
                VK10.vkEnumerateInstanceLayerProperties(count, null as VkLayerProperties.Buffer?),
                "Enumerating instance layers",
            )
            val properties = VkLayerProperties.malloc(count[0], stack)
            checkVulkanResult(VK10.vkEnumerateInstanceLayerProperties(count, properties), "Reading instance layers")
            buildSet(properties.capacity()) {
                for (index in 0 until properties.capacity()) {
                    add(properties[index].layerNameString())
                }
            }
        }

        private fun enumerateExtensions(): Set<String> = pushStack { stack ->
            val count = stack.ints(0)
            checkVulkanResult(
                VK10.vkEnumerateInstanceExtensionProperties(
                    null as String?,
                    count,
                    null as VkExtensionProperties.Buffer?
                ),
                "Enumerating instance extensions",
            )
            val properties = VkExtensionProperties.malloc(count[0], stack)
            checkVulkanResult(
                VK10.vkEnumerateInstanceExtensionProperties(null as String?, count, properties),
                "Reading instance extensions",
            )
            buildSet(properties.capacity()) {
                for (index in 0 until properties.capacity()) {
                    add(properties[index].extensionNameString())
                }
            }
        }
    }
}

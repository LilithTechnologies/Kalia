package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.core.Version
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.internal.vk.VulkanConstants
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.memory.MemoryHeap
import re.lilith.vulkan.api.memory.MemoryProperties
import re.lilith.vulkan.api.memory.MemoryType
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.types.enum.PhysicalDeviceType
import re.lilith.vulkan.api.types.flags.MemoryHeapFlags
import re.lilith.vulkan.api.types.flags.MemoryPropertyFlags
import re.lilith.vulkan.api.types.flags.QueueCapability
import re.lilith.vulkan.api.types.geometry.Extent3D

class PhysicalDevice internal constructor(
    internal val instance: VulkanInstance,
    val handle: VkPhysicalDevice,
    val properties: PhysicalDevicePropertiesView,
    val features: DeviceFeatureSet,
    val memoryProperties: MemoryProperties,
    val queueFamilies: List<QueueFamily>,
    val supportedExtensions: Set<String>,
) {
    fun supportsExtension(name: String): Boolean = name in supportedExtensions

    fun findQueueFamily(requiredCapabilities: QueueCapability): QueueFamily? =
        queueFamilies.firstOrNull { it.capabilities.contains(requiredCapabilities) }

    fun createLogicalDevice(configure: DeviceConfigBuilder.() -> Unit = {}): LogicalDevice =
        LogicalDevice.create(this, DeviceConfigBuilder(this).apply(configure).build())

    internal companion object {
        fun query(instance: VulkanInstance, handle: VkPhysicalDevice): PhysicalDevice = pushStack { stack ->
            val properties = VkPhysicalDeviceProperties.malloc(stack)
            VK10.vkGetPhysicalDeviceProperties(handle, properties)

            val features = VkPhysicalDeviceFeatures.malloc(stack)
            VK10.vkGetPhysicalDeviceFeatures(handle, features)

            val apiVersion = Version.decode(properties.apiVersion())
            val descriptorIndexingFeatures = if (apiVersion >= Version.V1_2) {
                VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack)
                    .sType(VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES)
            } else {
                null
            }
            val vulkan11Features = if (apiVersion >= Version.V1_1) {
                VkPhysicalDeviceVulkan11Features.calloc(stack)
                    .`sType$Default`()
            } else {
                null
            }
            if (vulkan11Features != null) {
                val featureChain = VkPhysicalDeviceFeatures2.calloc(stack)
                    .`sType$Default`()
                if (descriptorIndexingFeatures != null) {
                    descriptorIndexingFeatures.pNext(vulkan11Features.address())
                    featureChain.pNext(descriptorIndexingFeatures.address())
                } else {
                    featureChain.pNext(vulkan11Features.address())
                }
                VK11.vkGetPhysicalDeviceFeatures2(handle, featureChain)
            }

            val queueFamilyCount = stack.ints(0)
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(handle, queueFamilyCount, null)
            val queueFamilyProperties = VkQueueFamilyProperties.malloc(queueFamilyCount[0], stack)
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(handle, queueFamilyCount, queueFamilyProperties)

            val extensionCount = stack.ints(0)
            checkVulkanResult(
                VK10.vkEnumerateDeviceExtensionProperties(
                    handle,
                    null as String?,
                    extensionCount,
                    null as VkExtensionProperties.Buffer?
                ),
                "Enumerating device extensions",
            )
            val extensionProperties = VkExtensionProperties.malloc(extensionCount[0])
            checkVulkanResult(
                VK10.vkEnumerateDeviceExtensionProperties(handle, null as String?, extensionCount, extensionProperties),
                "Reading device extensions",
            )

            val extensionNames = extensionProperties.toExtensionNames()
            val pushDescriptorProperties =
                if (KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME in extensionNames) {
                    VkPhysicalDevicePushDescriptorPropertiesKHR.calloc(stack)
                        .sType(KHRPushDescriptor.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PUSH_DESCRIPTOR_PROPERTIES_KHR)
                } else {
                    null
                }
            val multiDrawProperties = if (EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME in extensionNames) {
                VkPhysicalDeviceMultiDrawPropertiesEXT.calloc(stack)
                    .`sType$Default`()
            } else {
                null
            }
            if (pushDescriptorProperties != null || multiDrawProperties != null) {
                var next = 0L
                if (multiDrawProperties != null) {
                    multiDrawProperties.pNext(next)
                    next = multiDrawProperties.address()
                }
                if (pushDescriptorProperties != null) {
                    pushDescriptorProperties.pNext(next)
                    next = pushDescriptorProperties.address()
                }
                val properties2 = VkPhysicalDeviceProperties2.calloc(stack)
                    .sType(VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2)
                    .pNext(next)
                VK11.vkGetPhysicalDeviceProperties2(handle, properties2)
            }

            val multiDrawFeatures =
                if (apiVersion >= Version.V1_1 && EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME in extensionNames) {
                    VkPhysicalDeviceMultiDrawFeaturesEXT.calloc(stack)
                        .sType(EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT)
                } else {
                    null
                }
            val vulkan13Features =
                if (apiVersion >= Version.V1_3) {
                    VkPhysicalDeviceVulkan13Features.calloc(stack)
                        .sType(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES)
                } else {
                    null
                }
            val dynamicRenderingFeatures =
                if (apiVersion < Version.V1_3 && KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME in extensionNames) {
                    VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack)
                        .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR)
                } else {
                    null
                }
            if (multiDrawFeatures != null || vulkan13Features != null || dynamicRenderingFeatures != null) {
                var next = 0L
                if (dynamicRenderingFeatures != null) {
                    dynamicRenderingFeatures.pNext(next)
                    next = dynamicRenderingFeatures.address()
                }
                if (vulkan13Features != null) {
                    vulkan13Features.pNext(next)
                    next = vulkan13Features.address()
                }
                if (multiDrawFeatures != null) {
                    multiDrawFeatures.pNext(next)
                    next = multiDrawFeatures.address()
                }
                val featureChain = VkPhysicalDeviceFeatures2.calloc(stack)
                    .`sType$Default`()
                    .pNext(next)
                VK11.vkGetPhysicalDeviceFeatures2(handle, featureChain)
            }

            val memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack)
            VK10.vkGetPhysicalDeviceMemoryProperties(handle, memoryProperties)

            PhysicalDevice(
                instance = instance,
                handle = handle,
                properties = properties.toView(
                    maxPushDescriptors = pushDescriptorProperties?.maxPushDescriptors(),
                    maxMultiDrawCount = multiDrawProperties?.maxMultiDrawCount(),
                ),
                features = features.toFeatureSet(
                    apiVersion = apiVersion,
                    extensionNames = extensionNames,
                    shaderDrawParameters = vulkan11Features?.shaderDrawParameters() == true,
                    descriptorIndexingFeatures = descriptorIndexingFeatures,
                    dynamicRendering = vulkan13Features?.dynamicRendering() == true || dynamicRenderingFeatures?.dynamicRendering() == true,
                    multiDrawFeatures = multiDrawFeatures,
                ),
                memoryProperties = memoryProperties.toView(),
                queueFamilies = queueFamilyProperties.toView(),
                supportedExtensions = extensionNames,
            )
        }

        private fun VkPhysicalDeviceProperties.toView(
            maxPushDescriptors: Int?,
            maxMultiDrawCount: Int?,
        ): PhysicalDevicePropertiesView = PhysicalDevicePropertiesView(
            name = deviceNameString(),
            type = when (deviceType()) {
                VulkanConstants.PhysicalDeviceTypes.integratedGpu -> PhysicalDeviceType.IntegratedGpu
                VulkanConstants.PhysicalDeviceTypes.discreteGpu -> PhysicalDeviceType.DiscreteGpu
                VulkanConstants.PhysicalDeviceTypes.virtualGpu -> PhysicalDeviceType.VirtualGpu
                VulkanConstants.PhysicalDeviceTypes.cpu -> PhysicalDeviceType.Cpu
                VulkanConstants.PhysicalDeviceTypes.other -> PhysicalDeviceType.Other
                else -> PhysicalDeviceType.Other
            },
            apiVersion = Version.decode(apiVersion()),
            driverVersion = Version.decode(driverVersion()),
            vendorId = vendorID(),
            deviceId = deviceID(),
            maxBoundDescriptorSets = limits().maxBoundDescriptorSets(),
            maxColorAttachments = limits().maxColorAttachments(),
            maxImageDimension2D = limits().maxImageDimension2D(),
            maxPushConstantsSize = limits().maxPushConstantsSize(),
            maxPushDescriptors = maxPushDescriptors,
            maxMultiDrawCount = maxMultiDrawCount,
            subTexelPrecisionBits = limits().subTexelPrecisionBits(),
            timestampPeriod = limits().timestampPeriod(),
            maxSamplerAnisotropy = limits().maxSamplerAnisotropy(),
        )

        private fun VkPhysicalDeviceFeatures.toFeatureSet(
            apiVersion: Version,
            extensionNames: Set<String>,
            shaderDrawParameters: Boolean,
            descriptorIndexingFeatures: VkPhysicalDeviceDescriptorIndexingFeatures?,
            dynamicRendering: Boolean,
            multiDrawFeatures: VkPhysicalDeviceMultiDrawFeaturesEXT?,
        ): DeviceFeatureSet = DeviceFeatureSet(
            robustBufferAccess = robustBufferAccess(),
            samplerAnisotropy = samplerAnisotropy(),
            sampleRateShading = sampleRateShading(),
            geometryShader = geometryShader(),
            tessellationShader = tessellationShader(),
            fillModeNonSolid = fillModeNonSolid(),
            wideLines = wideLines(),
            logicOp = logicOp(),
            multiDrawIndirect = multiDrawIndirect(),
            drawIndirectFirstInstance = drawIndirectFirstInstance(),
            shaderDrawParameters = shaderDrawParameters,
            shaderFloat64 = shaderFloat64(),
            shaderInt64 = shaderInt64(),
            shaderInt16 = shaderInt16(),
            descriptorIndexing = apiVersion >= Version.V1_2,
            descriptorBindingPartiallyBound = descriptorIndexingFeatures?.descriptorBindingPartiallyBound() == true,
            descriptorBindingVariableDescriptorCount = descriptorIndexingFeatures?.descriptorBindingVariableDescriptorCount() == true,
            descriptorBindingUpdateUnusedWhilePending = descriptorIndexingFeatures?.descriptorBindingUpdateUnusedWhilePending() == true,
            shaderSampledImageArrayNonUniformIndexing =
                descriptorIndexingFeatures?.shaderSampledImageArrayNonUniformIndexing() == true,
            runtimeDescriptorArray = descriptorIndexingFeatures?.runtimeDescriptorArray() == true,
            bufferDeviceAddress = apiVersion >= Version.V1_2,
            timelineSemaphore = apiVersion >= Version.V1_2,
            synchronization2 = apiVersion >= Version.V1_3,
            dynamicRendering = dynamicRendering,
            imagelessFramebuffer = apiVersion >= Version.V1_2 || "VK_KHR_imageless_framebuffer" in extensionNames,
            pushDescriptors = KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME in extensionNames,
            multiDraw = multiDrawFeatures?.multiDraw() == true,
        )

        private fun VkExtensionProperties.Buffer.toExtensionNames(): Set<String> = buildSet(capacity()) {
            for (index in 0 until capacity()) {
                add(this@toExtensionNames[index].extensionNameString())
            }
        }

        private fun VkQueueFamilyProperties.Buffer.toView(): List<QueueFamily> = List(capacity()) { index ->
            val family = this[index]
            QueueFamily(
                index = index,
                capabilities = QueueCapability.fromVk(family.queueFlags()),
                queueCount = family.queueCount(),
                timestampValidBits = family.timestampValidBits(),
                minImageTransferGranularity = Extent3D(
                    width = family.minImageTransferGranularity().width(),
                    height = family.minImageTransferGranularity().height(),
                    depth = family.minImageTransferGranularity().depth(),
                ),
            )
        }

        private fun VkPhysicalDeviceMemoryProperties.toView(): MemoryProperties = MemoryProperties(
            heaps = List(memoryHeapCount()) { index ->
                val heap = memoryHeaps(index)
                MemoryHeap(size = heap.size(), flags = MemoryHeapFlags.fromVk(heap.flags()))
            },
            types = List(memoryTypeCount()) { index ->
                val type = memoryTypes(index)
                MemoryType(
                    propertyFlags = MemoryPropertyFlags.fromVk(type.propertyFlags()),
                    heapIndex = type.heapIndex(),
                )
            },
        )
    }
}

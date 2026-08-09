package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.*
import re.lilith.vulkan.api.core.Version
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.internal.vk.pointerBufferOf
import re.lilith.vulkan.api.qol.pushStack

internal object LogicalDeviceFactory {
    fun create(physicalDevice: PhysicalDevice, config: DeviceConfig): LogicalDevice = pushStack { stack ->
        require(config.enabledExtensions.all(physicalDevice::supportsExtension)) {
            "Logical-device extension list contains unsupported entries."
        }
        require(!config.features.multiDraw || EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME in config.enabledExtensions) {
            "VK_EXT_multi_draw must be enabled when the multiDraw feature is requested."
        }
        require(
            !config.features.dynamicRendering ||
                    physicalDevice.instance.config.applicationInfo.apiVersion >= Version.V1_3 ||
                    KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME in config.enabledExtensions,
        ) {
            "VK_KHR_dynamic_rendering must be enabled when dynamicRendering is requested on Vulkan 1.2 devices."
        }

        val queueCreateInfos = VkDeviceQueueCreateInfo.calloc(config.queueRequests.size, stack)
        config.queueRequests.forEachIndexed { index, request ->
            queueCreateInfos[index]
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(request.familyIndex)
                .pQueuePriorities(stack.floats(*request.priorities.toFloatArray()))
        }

        val coreFeatures = VkPhysicalDeviceFeatures.calloc(stack).apply {
            robustBufferAccess(config.features.robustBufferAccess)
            samplerAnisotropy(config.features.samplerAnisotropy)
            sampleRateShading(config.features.sampleRateShading)
            geometryShader(config.features.geometryShader)
            tessellationShader(config.features.tessellationShader)
            fillModeNonSolid(config.features.fillModeNonSolid)
            wideLines(config.features.wideLines)
            logicOp(config.features.logicOp)
            multiDrawIndirect(config.features.multiDrawIndirect)
            drawIndirectFirstInstance(config.features.drawIndirectFirstInstance)
            shaderFloat64(config.features.shaderFloat64)
            shaderInt64(config.features.shaderInt64)
            shaderInt16(config.features.shaderInt16)
        }

        val apiVersion = physicalDevice.properties.apiVersion
        val useDynamicRenderingExtension =
            config.features.dynamicRendering &&
                    physicalDevice.instance.config.applicationInfo.apiVersion < Version.V1_3 &&
                    KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME in config.enabledExtensions
        val useVulkan11 = config.features.shaderDrawParameters
        val useVulkan12 = config.features.descriptorIndexing ||
                config.features.descriptorBindingPartiallyBound ||
                config.features.descriptorBindingVariableDescriptorCount ||
                config.features.descriptorBindingUpdateUnusedWhilePending ||
                config.features.runtimeDescriptorArray ||
                config.features.bufferDeviceAddress ||
                config.features.timelineSemaphore ||
                config.features.imagelessFramebuffer
        val useVulkan13 =
            config.features.synchronization2 || (config.features.dynamicRendering && !useDynamicRenderingExtension)

        require(!useVulkan11 || apiVersion >= Version.V1_1) {
            "Requested Vulkan 1.1 features on a device that only supports $apiVersion."
        }
        require(!useVulkan12 || apiVersion >= Version.V1_2) {
            "Requested Vulkan 1.2 features on a device that only supports $apiVersion."
        }
        require(!useVulkan13 || apiVersion >= Version.V1_3) {
            "Requested Vulkan 1.3 features on a device that only supports $apiVersion."
        }

        val feature11 = if (useVulkan11) {
            VkPhysicalDeviceVulkan11Features.calloc(stack)
                .`sType$Default`()
                .shaderDrawParameters(config.features.shaderDrawParameters)
        } else {
            null
        }

        val feature12 = if (useVulkan12) {
            VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType(VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                .descriptorIndexing(config.features.descriptorIndexing)
                .descriptorBindingPartiallyBound(config.features.descriptorBindingPartiallyBound)
                .descriptorBindingVariableDescriptorCount(config.features.descriptorBindingVariableDescriptorCount)
                .descriptorBindingUpdateUnusedWhilePending(config.features.descriptorBindingUpdateUnusedWhilePending)
                .runtimeDescriptorArray(config.features.runtimeDescriptorArray)
                .bufferDeviceAddress(config.features.bufferDeviceAddress)
                .timelineSemaphore(config.features.timelineSemaphore)
                .imagelessFramebuffer(config.features.imagelessFramebuffer)
                .also { features12 ->
                    if (feature11 != null) {
                        features12.pNext(feature11.address())
                    }
                }
        } else {
            null
        }

        val feature13 = if (useVulkan13) {
            VkPhysicalDeviceVulkan13Features.calloc(stack)
                .sType(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES)
                .synchronization2(config.features.synchronization2)
                .dynamicRendering(config.features.dynamicRendering)
                .also { features13 ->
                    when {
                        feature12 != null -> features13.pNext(feature12.address())
                        feature11 != null -> features13.pNext(feature11.address())
                    }
                }
        } else {
            null
        }

        val dynamicRenderingFeatures = if (useDynamicRenderingExtension) {
            VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR)
                .dynamicRendering(true)
                .also { dynamicRendering ->
                    when {
                        feature12 != null -> dynamicRendering.pNext(feature12.address())
                        feature11 != null -> dynamicRendering.pNext(feature11.address())
                    }
                }
        } else {
            null
        }

        val featureMultiDraw = if (config.features.multiDraw) {
            VkPhysicalDeviceMultiDrawFeaturesEXT.calloc(stack)
                .sType(EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT)
                .multiDraw(true)
                .also { featuresMultiDraw ->
                    when {
                        feature13 != null -> featuresMultiDraw.pNext(feature13.address())
                        dynamicRenderingFeatures != null -> featuresMultiDraw.pNext(dynamicRenderingFeatures.address())
                        feature12 != null -> featuresMultiDraw.pNext(feature12.address())
                        feature11 != null -> featuresMultiDraw.pNext(feature11.address())
                    }
                }
        } else {
            null
        }

        val createInfo = VkDeviceCreateInfo.calloc(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            .pQueueCreateInfos(queueCreateInfos)
            .ppEnabledExtensionNames(
                if (config.enabledExtensions.isEmpty()) null else stack.pointerBufferOf(config.enabledExtensions),
            )
            .pEnabledFeatures(coreFeatures)

        when {
            featureMultiDraw != null -> createInfo.pNext(featureMultiDraw.address())
            feature13 != null -> createInfo.pNext(feature13.address())
            dynamicRenderingFeatures != null -> createInfo.pNext(dynamicRenderingFeatures.address())
            feature12 != null -> createInfo.pNext(feature12.address())
            feature11 != null -> createInfo.pNext(feature11.address())
        }

        val pointer = stack.mallocPointer(1)
        checkVulkanResult(
            VK10.vkCreateDevice(physicalDevice.handle, createInfo, null, pointer),
            "Creating logical device"
        )
        val handle = VkDevice(pointer[0], physicalDevice.handle, createInfo)
        val device = LogicalDevice(physicalDevice, handle, config)

        val queues = buildMap<Int, List<Queue>> {
            config.queueRequests.forEach { request ->
                val familyQueues = List(request.priorities.size) { queueIndex ->
                    val queuePointer = stack.mallocPointer(1)
                    VK10.vkGetDeviceQueue(handle, request.familyIndex, queueIndex, queuePointer)
                    Queue(device, VkQueue(queuePointer[0], handle), request.familyIndex, queueIndex)
                }
                put(request.familyIndex, familyQueues)
            }
        }

        device.initializeQueues(queues)
        device
    }
}


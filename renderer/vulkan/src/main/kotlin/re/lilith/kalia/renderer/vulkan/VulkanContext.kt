package re.lilith.kalia.renderer.vulkan

import org.lwjgl.vulkan.EXTMultiDraw
import org.lwjgl.vulkan.KHRDynamicRendering
import re.lilith.kalia.renderer.device.*
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.Vulkan
import re.lilith.vulkan.api.command.CommandPool
import re.lilith.vulkan.api.core.Version
import re.lilith.vulkan.api.descriptor.PUSH_DESCRIPTOR_EXTENSION_NAME
import re.lilith.vulkan.api.device.*
import re.lilith.vulkan.api.instance.VulkanInstance
import re.lilith.vulkan.api.memory.MemoryAllocator
import re.lilith.vulkan.api.pipeline.PipelineCache
import re.lilith.vulkan.api.presentation.*
import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.enum.PhysicalDeviceType
import re.lilith.vulkan.api.types.flags.QueueCapability
import java.util.concurrent.locks.ReentrantLock

internal class VulkanContext private constructor(
    val instance: VulkanInstance,
    val surface: Surface,
    val physicalDevice: PhysicalDevice,
    val device: LogicalDevice,
    val graphicsQueue: Queue,
    val presentQueue: Queue,
    val graphicsFamilyIndex: Int,
    val presentFamilyIndex: Int,
    val commandPool: CommandPool,
    val allocator: MemoryAllocator,
    val pipelineCache: PipelineCache,
    val depthFormat: Format,
) : AutoCloseable {
    // A VkQueue must be externally synchronised
    private val queueLock = ReentrantLock()

    fun <T> withQueueLock(action: () -> T): T {
        queueLock.lock()
        return try {
            action()
        } finally {
            queueLock.unlock()
        }
    }

    val capabilities: DeviceCapabilities = DeviceCapabilities(
        backend = BackendId.VULKAN,
        adapterName = physicalDevice.properties.name,
        driverVersion = physicalDevice.properties.driverVersion.toString(),
        apiVersion = physicalDevice.properties.apiVersion.toString(),
        vendorName = getVendorName(physicalDevice.properties.vendorId),
        maxTextureSize = physicalDevice.properties.maxImageDimension2D,
        maxColorAttachments = physicalDevice.properties.maxColorAttachments,
        supportsAnisotropy = physicalDevice.features.samplerAnisotropy,
        maxAnisotropy = physicalDevice.properties.maxSamplerAnisotropy,
        supportedDepthFormats = listOfNotNull(Convert.textureFormat(depthFormat)),
        // Overridden by the device, which owns the slot ring
        framesInFlight = 1,
        subTexelPrecisionBits = physicalDevice.properties.subTexelPrecisionBits,
    )

    private fun getVendorName(vendorId: Int): String {
        return when (vendorId) {
            0x10DE -> "NVIDIA Corporation"
            0x1002 -> "ATI Technologies"
            0x8086 -> "Intel Corporation"
            0x13B5 -> "Arm Limited"
            0x5143 -> "Qualcomm Incorporated"
            0x106B -> "Apple Inc."
            0x1010 -> "Imagination Technologies Limited"
            0x144D -> "Samsung"
            0x5E3A -> "Vivante Corporation"
            0x10001 -> "Mesa"
            else -> "Unknown Vendor (0x${Integer.toHexString(vendorId).uppercase()})"
        }
    }

    val usesDynamicRenderingExtension: Boolean =
        instance.config.applicationInfo.apiVersion < Version.V1_3

    val supportsPushDescriptors: Boolean = PUSH_DESCRIPTOR_EXTENSION_NAME in device.enabledExtensions
    val supportsMultiDraw: Boolean = device.config.features.multiDraw

    val supportsMultiDrawIndirect: Boolean = device.config.features.multiDrawIndirect

    val maxMultiDrawCount: Int = physicalDevice.properties.maxMultiDrawCount ?: 1

    override fun close() {
        device.waitIdle()
        device.close()
        surface.close()
        instance.close()
    }

    companion object {
        fun isSupported(surface: PlatformSurface): Boolean =
            surface.windowSystem == WindowSystem.SDL &&
                    surface.nativeHandle != 0L &&
                    Vulkan.supportedApiVersion >= Version.V1_2

        fun create(platformSurface: PlatformSurface, settings: DeviceSettings): VulkanContext {
            require(platformSurface.windowSystem == WindowSystem.SDL) {
                "The Vulkan backend currently creates surfaces through SDL only."
            }

            val instance = Vulkan.createInstance {
                applicationName = "Minecraft JE"
                engineName = "Kalia Engine"
                applicationVersion = Version(1, 0, 0)
                engineVersion = Version(1, 0, 0)
                apiVersion = Version.V1_2

                if (settings.validation) {
                    enableValidation()
                    enableDebugUtils()
                }
                SdlSurface.requiredInstanceExtensions().forEach(::enableExtension)
            }

            val surface = try {
                instance.createSdlSurface(platformSurface.nativeHandle)
            } catch (failure: Throwable) {
                instance.close()
                throw failure
            }

            try {
                val physicalDevice = instance.selectPhysicalDevice(
                    selector = { candidate ->
                        when (candidate.properties.type) {
                            PhysicalDeviceType.DiscreteGpu -> 0
                            PhysicalDeviceType.IntegratedGpu -> 1
                            PhysicalDeviceType.VirtualGpu -> 2
                            PhysicalDeviceType.Cpu -> 3
                            PhysicalDeviceType.Other -> 4
                        }
                    },
                    predicate = { candidate ->
                        candidate.findQueueFamily(QueueCapability.Graphics) != null &&
                                candidate.findPresentQueueFamily(surface) != null &&
                                candidate.supportsExtension(SWAPCHAIN_EXTENSION_NAME) &&
                                candidate.features.dynamicRendering &&
                                candidate.properties.apiVersion >= Version.V1_2
                    },
                )

                val graphicsFamily = requireNotNull(physicalDevice.findQueueFamily(QueueCapability.Graphics))
                val presentFamily = requireNotNull(physicalDevice.findPresentQueueFamily(surface))

                val device = physicalDevice.createLogicalDevice {
                    requestQueues(graphicsFamily.index, 1.0f)
                    if (presentFamily.index != graphicsFamily.index) {
                        requestQueues(presentFamily.index, 1.0f)
                    }
                    enableExtension(SWAPCHAIN_EXTENSION_NAME)
                    enableExtension(KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME)
                    if (physicalDevice.features.pushDescriptors) {
                        enableExtension(PUSH_DESCRIPTOR_EXTENSION_NAME)
                    }
                    if (physicalDevice.features.multiDraw) {
                        enableExtension(EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME)
                    }
                    features = DeviceFeatureSet(
                        samplerAnisotropy = physicalDevice.features.samplerAnisotropy,
                        fillModeNonSolid = physicalDevice.features.fillModeNonSolid,
                        wideLines = physicalDevice.features.wideLines,
                        multiDrawIndirect = physicalDevice.features.multiDrawIndirect,
                        timelineSemaphore = physicalDevice.features.timelineSemaphore,
                        dynamicRendering = true,
                        pushDescriptors = physicalDevice.features.pushDescriptors,
                        multiDraw = physicalDevice.features.multiDraw,
                    )
                }

                return VulkanContext(
                    instance = instance,
                    surface = surface,
                    physicalDevice = physicalDevice,
                    device = device,
                    graphicsQueue = device.queue(graphicsFamily.index),
                    presentQueue = device.queue(presentFamily.index),
                    graphicsFamilyIndex = graphicsFamily.index,
                    presentFamilyIndex = presentFamily.index,
                    commandPool = device.createCommandPool(graphicsFamily.index),
                    allocator = device.createMemoryAllocator(),
                    pipelineCache = device.createPipelineCache(VulkanPipelineCacheStore.load()),
                    depthFormat = physicalDevice.findSupportedDepthFormat(),
                )
            } catch (failure: Throwable) {
                surface.close()
                instance.close()
                throw failure
            }
        }
    }
}
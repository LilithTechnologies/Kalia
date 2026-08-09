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
    val transferQueue: Queue?,
    val transferFamilyIndex: Int,
    val transferCommandPool: CommandPool?,
    val computeQueue: Queue?,
    val computeFamilyIndex: Int,
    val computeCommandPool: CommandPool?,
) : AutoCloseable {
    private val queueLock = ReentrantLock()
    private val transferLock = ReentrantLock()

    fun <T> withQueueLock(action: () -> T): T {
        queueLock.lock()
        return try {
            action()
        } finally {
            queueLock.unlock()
        }
    }

    fun <T> withTransferLock(action: () -> T): T {
        transferLock.lock()
        return try {
            action()
        } finally {
            transferLock.unlock()
        }
    }

    private val computeLock = ReentrantLock()

    fun <T> withComputeLock(action: () -> T): T {
        computeLock.lock()
        return try {
            action()
        } finally {
            computeLock.unlock()
        }
    }

    /**
     * True when compute can run on a queue independent of graphics.
     */
    val hasAsyncCompute: Boolean get() = computeQueue != null

    val hasDedicatedTransfer: Boolean get() = transferQueue != null

    /**
     * Families that may touch a device-local buffer.
     */
    val bufferQueueFamilies: List<Int> =
        if (transferQueue != null) listOf(graphicsFamilyIndex, transferFamilyIndex) else emptyList()

    val capabilities: DeviceCapabilities = DeviceCapabilities(
        backend = BackendId.Vulkan,
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
        dedicatedTransferQueue = transferQueue != null,
        asyncCompute = computeQueue != null,
        subTexelPrecisionBits = physicalDevice.properties.subTexelPrecisionBits,
    )

    private fun getVendorName(vendorId: Int): String {
        return when (vendorId) {
            0x10DE -> "NVIDIA Corporation"
            0x1002, 0x1022 -> "ATI Technologies" // Apparently, AMD has two. Took this from VulkanMod.
            0x8086 -> "Intel Corporation"
            0x13B5 -> "ARM"
            0x5143 -> "Qualcomm Incorporated"
            0x106B -> "Apple Inc."
            0x1010 -> "Imagination Technologies"
            0x144D -> "Samsung"
            0x5E3A -> "Vivante"
            0x10001 -> "Mesa"
            0x1AE0 -> "Google"
            else -> "Unknown Vendor (0x${Integer.toHexString(vendorId).uppercase()})"
        }
    }

    val usesDynamicRenderingExtension: Boolean =
        instance.config.applicationInfo.apiVersion < Version.V1_3

    val supportsPushDescriptors: Boolean = PUSH_DESCRIPTOR_EXTENSION_NAME in device.enabledExtensions
    val supportsMultiDraw: Boolean = device.config.features.multiDraw

    val supportsWideLines: Boolean = device.config.features.wideLines
    val supportsFillModeNonSolid: Boolean = device.config.features.fillModeNonSolid
    val supportsLogicOp: Boolean = device.config.features.logicOp

    val supportsMultiDrawIndirect: Boolean = device.config.features.multiDrawIndirect

    val maxMultiDrawCount: Int = physicalDevice.properties.maxMultiDrawCount ?: 1

    override fun close() {
        device.waitIdle()
        transferCommandPool?.close()
        computeCommandPool?.close()
        device.close()
        surface.close()
        instance.close()
    }

    companion object {
        internal fun findTransferFamily(families: List<QueueFamily>, graphicsFamilyIndex: Int): QueueFamily? {
            val candidates = families.filter { family ->
                family.index != graphicsFamilyIndex &&
                        family.queueCount > 0 &&
                        QueueCapability.Transfer in family.capabilities &&
                        QueueCapability.Graphics !in family.capabilities
            }
            return candidates.firstOrNull { QueueCapability.Compute !in it.capabilities }
                ?: candidates.firstOrNull()
        }

        internal class ComputeSelection(val family: QueueFamily, val queueIndex: Int)

        internal fun findComputeFamily(
            families: List<QueueFamily>,
            graphicsFamilyIndex: Int,
            transferFamilyIndex: Int,
        ): ComputeSelection? {
            val candidates = families.filter { family ->
                family.index != graphicsFamilyIndex &&
                        family.queueCount > 0 &&
                        QueueCapability.Compute in family.capabilities &&
                        QueueCapability.Graphics !in family.capabilities
            }
            candidates.firstOrNull { it.index != transferFamilyIndex }?.let {
                return ComputeSelection(it, 0)
            }
            return candidates.firstOrNull { it.queueCount > 1 }?.let { ComputeSelection(it, 1) }
        }

        val debugNamesRequested: Boolean
            get() = System.getProperty("kalia.debugNames", "false").toBoolean()

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
                }
                if (settings.validation || debugNamesRequested) {
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
                val transferFamily = findTransferFamily(physicalDevice.queueFamilies, graphicsFamily.index)
                val computeFamily = findComputeFamily(
                    physicalDevice.queueFamilies,
                    graphicsFamily.index,
                    transferFamily?.index ?: -1,
                )

                val device = physicalDevice.createLogicalDevice {
                    requestQueues(graphicsFamily.index, 1.0f)
                    if (presentFamily.index != graphicsFamily.index) {
                        requestQueues(presentFamily.index, 1.0f)
                    }
                    if (transferFamily != null) {
                        requestQueues(transferFamily.index, 1.0f)
                    }
                    if (computeFamily != null && computeFamily.family.index != transferFamily?.index) {
                        requestQueues(computeFamily.family.index, 1.0f)
                    } else if (computeFamily != null) {
                        requestQueues(computeFamily.family.index, 1.0f, 1.0f)
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
                        logicOp = physicalDevice.features.logicOp,
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
                    transferQueue = transferFamily?.let { device.queue(it.index) },
                    transferFamilyIndex = transferFamily?.index ?: -1,
                    transferCommandPool = transferFamily?.let { device.createCommandPool(it.index) },
                    computeQueue = computeFamily?.let { device.queue(it.family.index, it.queueIndex) },
                    computeFamilyIndex = computeFamily?.family?.index ?: -1,
                    computeCommandPool = computeFamily?.let { device.createCommandPool(it.family.index) },
                )
            } catch (failure: Throwable) {
                surface.close()
                instance.close()
                throw failure
            }
        }
    }
}
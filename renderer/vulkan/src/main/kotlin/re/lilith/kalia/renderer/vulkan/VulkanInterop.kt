package re.lilith.kalia.renderer.vulkan

import org.lwjgl.vulkan.VK
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK12
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.interop.RawHandles
import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.enum.ImageLayout

object VulkanInterop {
    @JvmStatic
    fun vkInstanceAddress(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.instance.handle.address()
    }

    @JvmStatic
    fun vkPhysicalDeviceAddress(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.physicalDevice.handle.address()
    }

    @JvmStatic
    fun vkDeviceAddress(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.device.handle.address()
    }

    @JvmStatic
    fun vkQueueAddress(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.graphicsQueue.handle.address()
    }

    @JvmStatic
    fun graphicsQueueIndex(device: RenderDevice): Int {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.graphicsQueue.queueIndex
    }
    
    @JvmStatic
    fun vkGetInstanceProcAddr(): Long = VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr")

    @JvmStatic
    fun vkGetDeviceProcAddr(): Long = VK.getFunctionProvider().getFunctionAddress("vkGetDeviceProcAddr")

    @JvmStatic
    fun vkApiVersion(): Int = VK12.VK_API_VERSION_1_2

    @JvmStatic
    fun swapchainHandle(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.swapchain.swapchain.handle
    }

    @JvmStatic
    fun swapchainFormat(device: RenderDevice): TextureFormat {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.swapchain.format
    }

    @JvmStatic
    fun currentMainColorImageHandle(device: RenderDevice): Long {
        val vulkanRenderDevice = device as VulkanRenderDevice
        val acquired = vulkanRenderDevice.acquiredOrNull ?: return 0L
        return RawHandles.image(acquired.image)
    }

    @JvmStatic
    fun swapchainColorFormat(device: RenderDevice): Int = when (device.surfaceFormat) {
        TextureFormat.RGBA8 -> VK10.VK_FORMAT_R8G8B8A8_UNORM
        TextureFormat.BGRA8 -> VK10.VK_FORMAT_B8G8R8A8_UNORM
        else -> VK10.VK_FORMAT_UNDEFINED
    }


    @JvmStatic
    fun swapchainColorView(device: RenderDevice): Long = currentMainColorImageHandle(device)

    @JvmStatic
    fun swapchainWidth(device: RenderDevice): Int = device.surfaceExtent.width

    @JvmStatic
    fun swapchainHeight(device: RenderDevice): Int = device.surfaceExtent.height

    @JvmStatic
    fun imageHandle(texture: GpuTexture): Long =
        (texture as? VulkanTexture)?.let { RawHandles.image(it.image) } ?: 0L

    @JvmStatic
    fun imageFormat(texture: GpuTexture): Int = vkFormat(texture.format)

    @JvmStatic
    fun vkFormat(format: TextureFormat): Int = when (format) {
        TextureFormat.R8 -> VK10.VK_FORMAT_R8_UNORM
        TextureFormat.RG8 -> VK10.VK_FORMAT_R8G8_UNORM
        TextureFormat.RGBA8 -> VK10.VK_FORMAT_R8G8B8A8_UNORM
        TextureFormat.BGRA8 -> VK10.VK_FORMAT_B8G8R8A8_UNORM
        TextureFormat.RGBA16F -> VK10.VK_FORMAT_R16G16B16A16_SFLOAT
        TextureFormat.RGBA32F -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT
        TextureFormat.DEPTH32F -> VK10.VK_FORMAT_D32_SFLOAT
        TextureFormat.DEPTH24_STENCIL8 -> VK10.VK_FORMAT_D24_UNORM_S8_UINT
        TextureFormat.DEPTH32F_STENCIL8 -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT
    }

    @JvmStatic
    fun markExternallyWritten(texture: GpuTexture) {
        (texture as? VulkanTexture)?.layout = ImageLayout.ColorAttachmentOptimal
    }


    @JvmStatic
    fun markExternallySampled(texture: GpuTexture) {
        (texture as? VulkanTexture)?.layout = ImageLayout.ShaderReadOnlyOptimal
    }

    @JvmStatic
    fun <T> withQueueLock(device: RenderDevice, action: () -> T): T {
        val vulkanRenderDevice = device as VulkanRenderDevice
        return vulkanRenderDevice.context.withQueueLock(action)
    }

    @JvmStatic
    fun defaultDepthFormat(device: RenderDevice): Int = when (Convert.format(device.capabilities.supportedDepthFormats.first())) {
        Format.D24_UNorm_S8_UInt -> VK10.VK_FORMAT_D24_UNORM_S8_UINT
        Format.D32_SFloat -> VK10.VK_FORMAT_D32_SFLOAT
        Format.D32_SFloat_S8_UInt -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT
        else -> VK10.VK_FORMAT_UNDEFINED
    }
}
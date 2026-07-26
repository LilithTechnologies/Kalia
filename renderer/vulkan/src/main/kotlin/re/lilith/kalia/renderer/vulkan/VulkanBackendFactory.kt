package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.device.*

/**
 * Provides a [RenderDevice] backed by the Vulkan Graphics API.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class VulkanBackendFactory : RenderBackendFactory {
    override val id = BackendId.Vulkan

    override fun isSupported(surface: PlatformSurface): Boolean =
        runCatching { VulkanContext.isSupported(surface) }.getOrDefault(false)

    override fun create(surface: PlatformSurface, settings: DeviceSettings): RenderDevice {
        val context = VulkanContext.create(surface, settings)
        return runCatching { VulkanRenderDevice(context, surface, settings) }
            .getOrElse { failure ->
                context.close()
                throw failure
            }
    }
}

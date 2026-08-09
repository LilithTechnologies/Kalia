package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.device.BackendId
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.RenderBackendFactory
import re.lilith.kalia.renderer.device.RenderDevice

class HeadlessBackendFactory : RenderBackendFactory {
    override val id = BackendId.Headless

    override fun isSupported(surface: PlatformSurface): Boolean {
        require(surface.nativeHandle != 0L) { "The platform surface has a null pointer for its native handle. (${surface.nativeHandle})" }

        return true
    }

    override fun create(
        surface: PlatformSurface,
        settings: DeviceSettings
    ): RenderDevice {
        require(surface.nativeHandle != 0L) { "The platform surface has a null pointer for its native handle. (${surface.nativeHandle})" }

        return HeadlessRenderDevice()
    }
}
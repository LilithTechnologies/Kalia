package re.lilith.kalia.renderer.opengl

import re.lilith.kalia.renderer.device.*

class OpenGlBackendFactory : RenderBackendFactory {
    override val id = BackendId.OPENGL

    override fun isSupported(surface: PlatformSurface): Boolean =
        runCatching { OpenGlContext.isSupported(surface) }.getOrDefault(false)

    override fun create(surface: PlatformSurface, settings: DeviceSettings): RenderDevice {
        val context = OpenGlContext.create(surface, settings)
        return runCatching { OpenGlRenderDevice(context, surface, settings) }
            .getOrElse { failure ->
                context.close()
                throw failure
            }
    }
}

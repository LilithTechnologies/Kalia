package re.lilith.kalia.renderer.device

interface RenderBackendFactory {
    val id: BackendId

    fun isSupported(surface: PlatformSurface): Boolean

    fun create(surface: PlatformSurface, settings: DeviceSettings): RenderDevice
}
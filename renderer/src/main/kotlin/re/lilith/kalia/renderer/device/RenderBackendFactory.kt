package re.lilith.kalia.renderer.device

/**
 * Creates rendering devices for a specific graphics backend.
 *
 * @author Lunasa
 * @since 1.0.0
 */
interface RenderBackendFactory {
    /**
     * Identifier of the backend implemented by this factory.
     */
    val id: BackendId

    /**
     * Determines whether this backend can be used with the given surface.
     *
     * @param surface The presentation surface to test.
     * @return `true` if a device can be created for the surface.
     */
    fun isSupported(surface: PlatformSurface): Boolean

    /**
     * Creates a rendering device for the given surface.
     *
     * The returned device is expected to be fully initialized and ready to
     * create resources, record commands, and present rendered frames.
     *
     * @param surface The presentation surface that will receive rendered output.
     * @param settings Device creation settings.
     * @return A newly created rendering device.
     *
     * @throws Exception if device creation fails.
     */
    fun create(surface: PlatformSurface, settings: DeviceSettings): RenderDevice
}
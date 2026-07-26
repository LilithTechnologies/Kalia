package re.lilith.kalia.renderer.device

/**
 * Identifies a rendering backend implementation.
 *
 * @author Lunasa
 * @since 1.0.0
 */
interface BackendId {
    val displayName: String

    open class Named internal constructor(name: String) : BackendId { override val displayName = name }

    object Vulkan : Named("Vulkan")
    object OpenGL : Named("OpenGL")
    object Headless : Named("Headless")
}
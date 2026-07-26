package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.geometry.Extent

/**
 * Represents a platform-specific presentation surface.
 *
 * @author Lunasa
 * @since 1.0.0
 */
interface PlatformSurface {
    /**
     * Native platform handle identifying the presentation surface.
     *
     * The interpretation of this value is defined by [windowSystem].
     */
    val nativeHandle: Long

    /**
     * The window system that owns this surface.
     */
    val windowSystem: WindowSystem

    /**
     * Current framebuffer size in physical pixels.
     */
    val framebufferExtent: Extent
}
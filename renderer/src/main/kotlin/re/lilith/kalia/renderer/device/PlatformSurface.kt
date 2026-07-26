package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.geometry.Extent

interface PlatformSurface {
    val nativeHandle: Long
    val windowSystem: WindowSystem

    // Current framebuffer size in pixels, which may differ from the logical window size
    val framebufferExtent: Extent
}


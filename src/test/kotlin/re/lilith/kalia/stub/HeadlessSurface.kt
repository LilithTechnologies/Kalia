package re.lilith.kalia.stub

import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.WindowSystem
import re.lilith.kalia.renderer.geometry.Extent

class HeadlessSurface : PlatformSurface {
    override val nativeHandle = 1L
    override val windowSystem = WindowSystem.HEADLESS
    override val framebufferExtent = Extent(1920, 1080)
}
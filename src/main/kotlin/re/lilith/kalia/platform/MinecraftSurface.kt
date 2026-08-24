package re.lilith.kalia.platform

import org.lwjgl.opengl.Display
import pl.tomgirl.lumen.window.DisplaySdl
import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.WindowSystem
import re.lilith.kalia.renderer.geometry.Extent

class MinecraftSurface private constructor(override val nativeHandle: Long) : PlatformSurface {
    override val windowSystem = WindowSystem.SDL

    override val framebufferExtent get() = Extent(
            width = Display.getWidth().coerceAtLeast(1),
            height = Display.getHeight().coerceAtLeast(1),
        )

    companion object {
        var unavailableReason: String? = null
            private set

        fun detect(): MinecraftSurface? {
            if (!Display.isCreated()) {
                unavailableReason = "the game window has not been created yet"
                return null
            }

            val handle = DisplaySdl.instance().handle

            if (handle == 0L || handle == -1L) {
                unavailableReason = "the window handle is not valid yet"
                return null
            }

            unavailableReason = null
            return MinecraftSurface(handle)
        }
    }
}

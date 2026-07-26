package re.lilith.vulkan.api.presentation

import org.lwjgl.sdl.SDLVulkan
import org.lwjgl.system.MemoryUtil

object SdlSurface {
    fun requiredInstanceExtensions(): Set<String> {
        val extensions = SDLVulkan.SDL_Vulkan_GetInstanceExtensions() ?: return emptySet()
        return buildSet(extensions.remaining()) {
            for (index in 0 until extensions.remaining()) {
                add(MemoryUtil.memUTF8(extensions[index]))
            }
        }
    }
}

package re.lilith.kalia.platform

import org.lwjgl.sdl.SDLError.SDL_GetError
import org.lwjgl.sdl.SDLProperties.*
import org.lwjgl.sdl.SDLVideo.*
import org.lwjgl.sdl.SDLVulkan.SDL_Vulkan_LoadLibrary
import org.lwjgl.sdl.SDLVulkan.SDL_Vulkan_UnloadLibrary
import org.lwjgl.system.Configuration
import org.lwjgl.system.Library
import org.lwjgl.system.Platform
import org.lwjgl.system.SharedLibrary
import org.lwjgl.vulkan.VK
import pl.tomgirl.lenis.window.GpuSurface
import re.lilith.kalia.KaliaHooks

class KaliaGpuSurface : GpuSurface {
    private var window = 0L
    private var moltenVk: SharedLibrary? = null
    private var sdlVulkanLoaded = false

    override fun createWindow(title: String, width: Int, height: Int, resizable: Boolean): Long {
        check(window == 0L) { "Surface has already been created" }

        loadVulkanPortabilityLibrary()
        val properties = checkPointer(SDL_CreateProperties().toLong()).toInt()
        try {
            checkSdl(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_X_NUMBER, SDL_WINDOWPOS_CENTERED.toLong()))
            checkSdl(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_Y_NUMBER, SDL_WINDOWPOS_CENTERED.toLong()))
            checkSdl(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, width.toLong()))
            checkSdl(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, height.toLong()))
            checkSdl(SDL_SetStringProperty(properties, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title))
            checkSdl(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN, true))
            checkSdl(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true))
            checkSdl(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, resizable))

            Configuration.OPENGL_EXPLICIT_INIT.set(true)
            window = checkPointer(SDL_CreateWindowWithProperties(properties))
            return window
        } finally {
            SDL_DestroyProperties(properties)
        }
    }

    override fun makeCurrent() = Unit

    override fun releaseCurrent() = Unit

    override fun swapBuffers() = Unit

    override fun setVSyncEnabled(enabled: Boolean) {
        KaliaHooks.setVsync(enabled)
    }

    override fun destroy() {
        if (window != 0L) {
            SDL_DestroyWindow(window)
            window = 0L
        }
        if (sdlVulkanLoaded) {
            SDL_Vulkan_UnloadLibrary()
            sdlVulkanLoaded = false
        }
        moltenVk?.free()
        moltenVk = null
    }

    private fun loadVulkanPortabilityLibrary() {
        if (Platform.get() != Platform.MACOSX) {
            return
        }

        val configuredLibrary = Configuration.VULKAN_LIBRARY_NAME.get()
        if (configuredLibrary != null) {
            checkSdl(SDL_Vulkan_LoadLibrary(configuredLibrary))
            sdlVulkanLoaded = true
            return
        }

        val library = Library.loadNative(VK::class.java, "org.lwjgl.vulkan", "MoltenVK", true)
        try {
            val path = checkNotNull(library.path) { "LWJGL did not expose the path to its bundled MoltenVK library" }
            checkSdl(SDL_Vulkan_LoadLibrary(path))
            Configuration.VULKAN_LIBRARY_NAME.set(path)
            moltenVk = library
            sdlVulkanLoaded = true
        } catch (throwable: Throwable) {
            library.free()
            throw throwable
        }
    }

    private fun checkSdl(success: Boolean) {
        check(success) { "SDL error encountered: ${SDL_GetError()}" }
    }

    private fun checkPointer(pointer: Long): Long {
        check(pointer != 0L) { "SDL error encountered: ${SDL_GetError()}" }
        return pointer
    }
}

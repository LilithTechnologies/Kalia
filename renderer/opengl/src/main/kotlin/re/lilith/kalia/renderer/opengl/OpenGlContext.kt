package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.GL_MAX_COLOR_ATTACHMENTS
import org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT
import org.lwjgl.opengl.GL32C.GL_PROGRAM_POINT_SIZE
import org.lwjgl.opengl.GL45C
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.sdl.SDLPlatform.SDL_GetPlatform
import org.lwjgl.sdl.SDLVideo.*
import re.lilith.kalia.renderer.device.*
import re.lilith.kalia.renderer.format.TextureFormat

internal class OpenGlContext private constructor(
    val window: Long,
    private val handle: Long,
    val glCapabilities: GLCapabilities,
    val supportsClipControl: Boolean,
    val supportsBufferStorage: Boolean,
    val uniformOffsetAlignment: Int,
) : AutoCloseable {

    val supportsAnisotropy: Boolean =
        glCapabilities.OpenGL46 || glCapabilities.GL_EXT_texture_filter_anisotropic

    val capabilities: DeviceCapabilities = DeviceCapabilities(
        backend = BackendId.OPENGL,
        adapterName = glGetString(GL_RENDERER) ?: "Unknown",
        driverVersion = glGetString(GL_VERSION) ?: "Unknown",
        apiVersion = glGetString(GL_VERSION) ?: "Unknown",
        vendorName = glGetString(GL_VENDOR) ?: "Unknown",
        maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE),
        maxColorAttachments = glGetInteger(GL_MAX_COLOR_ATTACHMENTS),
        supportsAnisotropy = supportsAnisotropy,
        maxAnisotropy = if (supportsAnisotropy) {
            glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
        } else {
            1f
        },
        supportedDepthFormats = listOf(TextureFormat.DEPTH32F, TextureFormat.DEPTH24_STENCIL8),
        // Overridden by the device, which owns the slot ring
        framesInFlight = 1,
        subTexelPrecisionBits = 8,
    )

    fun setSwapInterval(vsync: Boolean) {
        SDL_GL_SetSwapInterval(if (vsync) 1 else 0)
    }

    override fun close() {
        SDL_GL_DestroyContext(handle)
    }

    companion object {
        fun isSupported(surface: PlatformSurface): Boolean =
            surface.windowSystem == WindowSystem.SDL &&
                    surface.nativeHandle != 0L &&
                    (SDL_GetWindowFlags(surface.nativeHandle) and SDL_WINDOW_OPENGL) != 0L

        fun create(platformSurface: PlatformSurface, settings: DeviceSettings): OpenGlContext {
            require(platformSurface.windowSystem == WindowSystem.SDL) {
                "The OpenGL backend currently creates contexts through SDL only."
            }
            require((SDL_GetWindowFlags(platformSurface.nativeHandle) and SDL_WINDOW_OPENGL) != 0L) {
                "The game window was not created with SDL_WINDOW_OPENGL."
            }

            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 4))
            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 1))
            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE))
            if ("macOS" == SDL_GetPlatform()) {
                // macOS refuses core contexts that are not forward compatible
                check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG))
            }

            val handle = SDL_GL_CreateContext(platformSurface.nativeHandle)
            check(handle != 0L) { "SDL was unable to create an OpenGL 4.1 core context." }

            try {
                check(SDL_GL_MakeCurrent(platformSurface.nativeHandle, handle)) {
                    "SDL was unable to make the OpenGL context current."
                }
                if (GL.getFunctionProvider() == null) {
                    GL.create { name -> SDL_GL_GetProcAddress(name) }
                }
                val caps = GL.createCapabilities()
                check(caps.OpenGL41) { "Kalia requires at least OpenGL 4.1 core." }

                val clipControl = caps.OpenGL45 || caps.GL_ARB_clip_control
                if (clipControl) {
                    GL45C.glClipControl(GL45C.GL_UPPER_LEFT, GL45C.GL_ZERO_TO_ONE)
                } else {
                    glDepthRange(0.0, 1.0)
                }

                glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
                glEnable(GL_PROGRAM_POINT_SIZE)
                SDL_GL_SetSwapInterval(if (settings.vsync) 1 else 0)

                return OpenGlContext(
                    window = platformSurface.nativeHandle,
                    handle = handle,
                    glCapabilities = caps,
                    supportsClipControl = clipControl,
                    supportsBufferStorage = caps.OpenGL44 || caps.GL_ARB_buffer_storage,
                    uniformOffsetAlignment = glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT).coerceAtLeast(1),
                )
            } catch (failure: Throwable) {
                SDL_GL_DestroyContext(handle)
                throw failure
            }
        }
    }
}

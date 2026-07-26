package re.lilith.kalia.mixins.sdl;

import io.github.moehreag.legacylwjgl3.SDLPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.opengl.SDLDisplay;
import org.lwjgl.sdl.SDLPlatform;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLProperties.SDL_DestroyProperties;
import static org.lwjgl.sdl.SDLProperties.SDL_SetBooleanProperty;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLVideo.SDL_ShowWindow;
import static org.lwjgl.system.MemoryStack.stackPush;

@Mixin(SDLDisplay.class)
public abstract class MixinSDLDisplay {
    @Shadow
    public abstract void checkSdlError(boolean success);

    @Shadow
    private int windowedWidth;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private int windowedHeight;

    @Shadow
    private @NotNull String title;

    @Shadow
    private boolean resizable;

    @Shadow
    private long handle;

    @Shadow
    private long glContext;

    @Shadow
    protected abstract long checkSdlError(long resultPointer);

    @Shadow
    private int windowedY;

    @Shadow
    private int y;

    @Shadow
    private int x;

    @Shadow
    private int windowedX;

    @Shadow
    public abstract void setFullscreen(boolean fullscreen);

    @Shadow
    private boolean useFullscreenDeferred;

    @Shadow
    private int framebufferWidth;

    @Shadow
    private int framebufferHeight;

    @Shadow
    private ByteBuffer @Nullable [] cached_icons;

    @Shadow
    public abstract int setIcon(@NotNull ByteBuffer[] icons);

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SwapWindow(J)Z"))
    boolean impl$update(long window) {
        // handled by kalia on render
        return true;
    }

    /**
     * @reason Do not initialize OpenGL
     * @author Lunasa
     */
    @Overwrite
    public void create(@NotNull PixelFormat pixelFormat) throws LWJGLException {
        windowedWidth = width;
        windowedHeight = height;
        // Configure SDL
        int props = SDL_CreateProperties();
        checkSdlError(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_X_NUMBER, SDL_WINDOWPOS_CENTERED));
        checkSdlError(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_Y_NUMBER, SDL_WINDOWPOS_CENTERED));
        checkSdlError(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, width));
        checkSdlError(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, height));

        checkSdlError(SDL_SetStringProperty(props, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title));
        checkSdlError(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_OPENGL_BOOLEAN, true));

        // the graphics API must never be handled by the windowing system
        // this is handed off to kalia which decides what to do

//        if (!SDLPlatforms.MAC_OS.equals(SDLPlatform.SDL_GetPlatform())) { // macOS does not support the compat profile
//            checkSdlError(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3));
//            checkSdlError(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 2));
//            checkSdlError(SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY));
//        }
//        checkSdlError(SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1));
//        checkSdlError(SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, pixelFormat.getAlphaBits()));
//        checkSdlError(SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, pixelFormat.getDepthBits()));
//        checkSdlError(SDL_GL_SetAttribute(SDL_GL_STENCIL_SIZE, pixelFormat.getStencilBits()));
//        checkSdlError(SDL_GL_SetAttribute(SDL_GL_STEREO, pixelFormat.isStereo() ? 1 : 0));

        checkSdlError(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true));
        checkSdlError(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, resizable));
        handle = checkSdlError(SDL_CreateWindowWithProperties(props));
        SDL_DestroyProperties(props);

//        glContext = checkSdlError(SDL_GL_CreateContext(handle));
//        checkSdlError(SDL_GL_LoadLibrary((ByteBuffer) null));
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
//        GL.create(SDLVideo::SDL_GL_GetProcAddress);
//        GL.createCapabilities(MemoryUtil::memCallocPointer);

        try (MemoryStack ms = stackPush()) {
            var xBox = ms.mallocInt(1);
            var yBox = ms.mallocInt(1);
            SDL_GetWindowPosition(handle, xBox, yBox);
            windowedX = x = xBox.get(0);
            windowedY = y = yBox.get(0);
            setFullscreen(useFullscreenDeferred );

            IntBuffer width = ms.mallocInt(1);
            IntBuffer height = ms.mallocInt(1);
            checkSdlError(SDL_GetWindowSizeInPixels(handle, width, height));
            framebufferWidth = Math.max(1, width.get(0));
            framebufferHeight = Math.max(1, height.get(0));
        }

        Mouse.create();
        Keyboard.create();
        checkSdlError(SDL_ShowWindow(handle));
        if (cached_icons != null) {
            setIcon(cached_icons);
        }
    }
}

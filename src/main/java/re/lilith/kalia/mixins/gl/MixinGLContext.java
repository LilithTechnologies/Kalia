package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.lilith.kalia.renderer.utility.UnsafeHolder;

// "this is a great idea bro trust me"
@Mixin(GLContext.class)
public abstract class MixinGLContext {
    @Unique
    private static ContextCapabilities fakeCaps;

    @Inject(method = "getCapabilities", at = @At("HEAD"), cancellable = true)
    private static void injectGetCapabilities(CallbackInfoReturnable<ContextCapabilities> cir) {
        if (fakeCaps == null) {
            fakeCaps = createFakeCaps();
        }

        cir.setReturnValue(fakeCaps);
    }

    @Unique
    private static ContextCapabilities createFakeCaps() {
        try {
            ContextCapabilities caps = (ContextCapabilities) UnsafeHolder.UNSAFE.allocateInstance(ContextCapabilities.class);

            caps.OpenGL13 = true;
            caps.OpenGL14 = true;
            caps.OpenGL15 = true;
            caps.OpenGL20 = true;
            caps.OpenGL21 = true;
            caps.OpenGL30 = true;

            caps.GL_ARB_multitexture = true;
            caps.GL_ARB_texture_env_combine = true;
            caps.GL_EXT_blend_func_separate = true;
            caps.GL_ARB_framebuffer_object = true;
            caps.GL_EXT_framebuffer_object = true;
            caps.GL_ARB_vertex_shader = true;
            caps.GL_ARB_fragment_shader = true;
            caps.GL_ARB_shader_objects = true;
            caps.GL_ARB_vertex_buffer_object = true;

            return caps;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create fake GL caps", e);
        }
    }
}

package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.KaliaEngine;
import re.lilith.kalia.gl.GlBridge;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;

@Mixin(GL11.class)
public class MixinGL11_PipelineState {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glEnable(int capability) {
        GlBridge.setCapability(capability, true);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDisable(int capability) {
        GlBridge.setCapability(capability, false);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static boolean glIsEnabled(int capability) {
        return GlBridge.isCapabilityEnabled(capability);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glClear(int mask) {
        GlBridge.clear(mask);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glClearColor(float red, float green, float blue, float alpha) {
        GlBridge.clearColor(red, green, blue, alpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glClearDepth(double depth) {
        GlBridge.clearDepth(depth);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDepthMask(boolean enabled) {
        GlBridge.depthMask(enabled);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDepthFunc(int function) {
        GlBridge.depthFunc(function);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBlendFunc(int source, int destination) {
        GlBridge.blendFunc(source, destination);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GlBridge.colorMask(red, green, blue, alpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glPolygonOffset(float factor, float units) {
        GlBridge.polygonOffset(factor, units);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glPolygonMode(int face, int mode) {
        GlBridge.polygonMode(mode);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glLineWidth(float width) {
        GlBridge.lineWidth(width);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glViewport(int x, int y, int width, int height) {
        GlBridge.viewport(x, y, width, height);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glScissor(int x, int y, int width, int height) {
        GlBridge.scissor(x, y, width, height);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glAlphaFunc(int function, float reference) {
        GlBridge.alphaFunc(function, reference);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGetInteger(int name) {
        return GlBridge.getInteger(name);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGetError() {
        return 0;
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static String glGetString(int name) {
        var device = KaliaEngine.INSTANCE.getDevice();

        if (device == null) return "<unavailable>";

        return switch (name) {
            case GL_VENDOR -> device.getCapabilities().getVendorName();
            case GL_RENDERER -> GlBridge.rendererName();
            case GL_VERSION ->
                    "Kalia/" + device.getCapabilities().getBackend().getDisplayName() + " " + device.getCapabilities().getApiVersion();

            default -> "";
        };
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glHint(int target, int hint) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFinish() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFlush() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glPixelStorei(int name, int value) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glShadeModel(int mode) {
    }
}

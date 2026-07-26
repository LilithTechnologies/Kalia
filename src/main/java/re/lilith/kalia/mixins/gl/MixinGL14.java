package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.gl.GlBridge;

@Mixin(GL14.class)
public class MixinGL14 {

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBlendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        GlBridge.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBlendEquation(int op) {
        GlBridge.blendEquation(op);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBlendColor(float red, float green, float blue, float alpha) {
    }
}

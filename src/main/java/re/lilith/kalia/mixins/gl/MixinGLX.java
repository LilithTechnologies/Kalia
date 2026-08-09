package re.lilith.kalia.mixins.gl;

import com.mojang.blaze3d.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.gl.GlBridge;

@Mixin(GLX.class)
public class MixinGLX {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void gl13MultiTexCoord2f(int unit, float s, float t) {
        GlBridge.multiTexCoord(unit, s, t);
    }
}

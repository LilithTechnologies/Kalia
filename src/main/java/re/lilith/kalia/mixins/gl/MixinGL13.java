package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.gl.GlBridge;
import re.lilith.kalia.gl.TextureUnits;

@Mixin(GL13.class)
public class MixinGL13 {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glActiveTexture(int unit) {
        TextureUnits.activeTexture(unit);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glMultiTexCoord2f(int unit, float s, float t) {
        GlBridge.multiTexCoord(unit, s, t);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glClientActiveTexture(int unit) {
    }
}

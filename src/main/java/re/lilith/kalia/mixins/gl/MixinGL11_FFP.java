package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.gl.GlBridge;

import java.nio.FloatBuffer;

@Mixin(GL11.class)
public class MixinGL11_FFP {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFogfv(int name, FloatBuffer values) {
        GlBridge.fog(name, values);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFogi(int name, int value) {
        if (name == 0x0B65) {
            GlBridge.fogMode(value);
        }
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glLightfv(int light, int name, FloatBuffer values) {
        GlBridge.light(light, name, values);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glLightModelfv(int name, FloatBuffer values) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexEnvfv(int target, int name, FloatBuffer values) {
        GlBridge.texEnvColor(name, values);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexEnvi(int target, int name, int value) {
        GlBridge.texEnv(name, value);
    }
}

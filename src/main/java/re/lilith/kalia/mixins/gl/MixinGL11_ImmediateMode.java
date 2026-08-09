package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.frame.draw.ImmediateMode;

@Mixin(GL11.class)
public class MixinGL11_ImmediateMode {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBegin(int mode) {
        ImmediateMode.INSTANCE.begin(mode);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glEnd() {
        ImmediateMode.INSTANCE.end();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexCoord2f(float s, float t) {
        ImmediateMode.INSTANCE.texCoord(s, t);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glVertex3f(float x, float y, float z) {
        ImmediateMode.INSTANCE.vertex(x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glNormal3f(float x, float y, float z) {
        // todo
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glEnableClientState(int statae) {
        // todo
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDisableClientState(int statae) {
        // todo
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glVertexPointer(int size, int type, int stride, long pointer) {
        // todo
    }
}

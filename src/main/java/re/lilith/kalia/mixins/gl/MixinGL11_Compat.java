package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.draw.DisplayLists;

import java.nio.ByteBuffer;

@Mixin(GL11.class)
public class MixinGL11_Compat {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glCallList(int list) {
        DisplayLists.INSTANCE.call(list);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glColorPointer(int size, int type, int stride, long pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexCoordPointer(int size, int type, int stride, long pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glNormalPointer(int type, int stride, long pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDrawArrays(int mode, int first, int count) {
        // it's only client arrays that can use this
        // that's dead so...
    }
}
